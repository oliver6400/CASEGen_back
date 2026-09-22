package com.casegen.project.service;

import com.casegen.auth.domain.User;
import com.casegen.auth.repository.UserRepository;
import com.casegen.project.domain.*;
import com.casegen.project.dto.ProjectSharingDtos.*;
import com.casegen.project.repository.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectSharingService {
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final ProjectInvitationRepository invitations;
    private final UserRepository users;
    private final ProjectAuthorizationService authorization;
    public ProjectSharingService(ProjectRepository projects, ProjectMemberRepository members, ProjectInvitationRepository invitations,
            UserRepository users, ProjectAuthorizationService authorization) {
        this.projects=projects; this.members=members; this.invitations=invitations; this.users=users; this.authorization=authorization;
    }
    @Transactional
    public VisibilityResponse visibility(UUID projectId, VisibilityRequest request) {
        Project project=lockProject(projectId); authorization.requireManageMembers(projectId);
        project.changeVisibility(request.visibility()); projects.saveAndFlush(project);
        return new VisibilityResponse(projectId, project.getVisibility());
    }
    @Transactional(readOnly=true)
    public List<MemberResponse> members(UUID projectId) {
        authorization.requireRead(projectId);
        boolean showEmail=authorization.getAccess(projectId).canManageMembers();
        return members.findByProjectId(projectId).stream().filter(m -> m.getStatus()==ProjectMemberStatus.ACTIVE)
                .sorted(java.util.Comparator.comparing(ProjectMember::getRole).thenComparing(m -> m.getUser().getDisplayName()))
                .map(m -> new MemberResponse(m.getId(),m.getUser().getId(),m.getUser().getDisplayName(),showEmail?m.getUser().getEmail():null,m.getRole(),m.getStatus())).toList();
    }
    @Transactional
    public MemberResponse changeRole(UUID projectId, UUID memberId, MemberRoleRequest request) {
        lockProject(projectId); authorization.requireManageMembers(projectId);
        ProjectMember member=mutableMember(projectId,memberId);
        member.changeRole(ProjectMemberRole.valueOf(request.role().name())); members.saveAndFlush(member);
        return new MemberResponse(member.getId(),member.getUser().getId(),member.getUser().getDisplayName(),member.getUser().getEmail(),member.getRole(),member.getStatus());
    }
    @Transactional
    public void revokeMember(UUID projectId, UUID memberId) {
        lockProject(projectId); authorization.requireManageMembers(projectId);
        ProjectMember member=mutableMember(projectId,memberId); member.revoke(); members.saveAndFlush(member);
        invitations.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .filter(i -> i.getStatus()==ProjectInvitationStatus.PENDING && i.getEmail().equalsIgnoreCase(member.getUser().getEmail()))
                .forEach(ProjectInvitation::revoke);
    }
    @Transactional
    public InvitationResponse invite(UUID projectId, InviteRequest request) {
        Project project=lockProject(projectId); User inviter=authorization.requireManageMembers(projectId);
        String email=request.email().trim().toLowerCase(Locale.ROOT);
        if (invitations.existsByProjectIdAndEmailIgnoreCaseAndStatusAndExpiresAtAfter(projectId,email,ProjectInvitationStatus.PENDING,Instant.now()))
            throw conflict("A pending invitation already exists for this email");
        users.findByEmailIgnoreCase(email).flatMap(u -> members.findByProjectIdAndUserIdAndStatus(projectId,u.getId(),ProjectMemberStatus.ACTIVE))
                .ifPresent(m -> { throw conflict("User is already an active member"); });
        // No token is exposed or used as authority: acceptance requires the recipient's authenticated email.
        String hash=invitationHash();
        return invitation(invitations.saveAndFlush(new ProjectInvitation(project,inviter,email,request.role(),hash,Instant.now().plus(7,ChronoUnit.DAYS))));
    }
    @Transactional(readOnly=true)
    public List<InvitationResponse> projectInvitations(UUID projectId) {
        authorization.requireManageMembers(projectId);
        return invitations.findByProjectIdOrderByCreatedAtDesc(projectId).stream().map(this::invitation).toList();
    }
    @Transactional(readOnly=true)
    public List<InvitationResponse> mine() {
        User user=authorization.currentUser();
        return invitations.findByEmailIgnoreCaseAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(user.getEmail(),ProjectInvitationStatus.PENDING,Instant.now()).stream().map(this::invitation).toList();
    }
    @Transactional
    public InvitationResponse accept(UUID invitationId) {
        ProjectInvitation invitation=lockInvitation(invitationId); User recipient=recipient(invitation);
        requirePending(invitation);
        ProjectMember member=members.findByProjectIdAndUserId(invitation.getProject().getId(),recipient.getId()).orElse(null);
        if (member==null) member=new ProjectMember(invitation.getProject(),recipient,ProjectMemberRole.valueOf(invitation.getRole().name()));
        else {
            if (member.getRole()==ProjectMemberRole.OWNER || member.getStatus()==ProjectMemberStatus.ACTIVE) throw conflict("User is already an active member or OWNER");
            member.activate(ProjectMemberRole.valueOf(invitation.getRole().name()));
        }
        members.saveAndFlush(member); invitation.accept(); invitations.saveAndFlush(invitation);
        return invitation(invitation);
    }
    @Transactional
    public InvitationResponse reject(UUID invitationId) {
        ProjectInvitation invitation=lockInvitation(invitationId); recipient(invitation); requirePending(invitation);
        invitation.reject(); return invitation(invitations.saveAndFlush(invitation));
    }
    @Transactional
    public InvitationResponse revokeInvitation(UUID projectId, UUID invitationId) {
        lockProject(projectId); authorization.requireManageMembers(projectId);
        ProjectInvitation invitation=invitations.findById(invitationId).filter(i -> i.getProject().getId().equals(projectId))
                .orElseThrow(() -> notFound("Invitation not found in project"));
        if (invitation.getStatus()!=ProjectInvitationStatus.PENDING) throw conflict("Only pending invitations can be revoked");
        invitation.revoke(); return invitation(invitations.saveAndFlush(invitation));
    }
    private Project lockProject(UUID id) { return projects.findByIdForUpdate(id).orElseThrow(() -> notFound("Project not found")); }
    private ProjectInvitation lockInvitation(UUID id) {
        UUID projectId=invitations.findProjectIdByInvitationId(id).orElseThrow(() -> notFound("Invitation not found"));
        lockProject(projectId);
        return invitations.findById(id).orElseThrow(() -> notFound("Invitation not found"));
    }
    private User recipient(ProjectInvitation invitation) {
        User user=authorization.currentUser();
        if (!user.getEmail().equalsIgnoreCase(invitation.getEmail())) throw new AccessDeniedException("Invitation belongs to another email");
        return user;
    }
    private void requirePending(ProjectInvitation invitation) {
        if (invitation.getStatus()!=ProjectInvitationStatus.PENDING || !invitation.getExpiresAt().isAfter(Instant.now())) throw conflict("Invitation is not pending or has expired");
    }
    private ProjectMember mutableMember(UUID projectId, UUID id) {
        ProjectMember member=members.findById(id).filter(m -> m.getProject().getId().equals(projectId)).orElseThrow(() -> notFound("Member not found in project"));
        if (member.getRole()==ProjectMemberRole.OWNER) throw conflict("OWNER cannot be modified or revoked through this endpoint");
        if (member.getStatus()!=ProjectMemberStatus.ACTIVE) throw conflict("Member is not active");
        return member;
    }
    private InvitationResponse invitation(ProjectInvitation i) {
        var status=i.getStatus()==ProjectInvitationStatus.PENDING && !i.getExpiresAt().isAfter(Instant.now()) ? ProjectInvitationStatus.EXPIRED : i.getStatus();
        return new InvitationResponse(i.getId(),i.getProject().getId(),i.getProject().getName(),i.getEmail(),i.getRole(),status,i.getExpiresAt(),i.getCreatedAt());
    }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND,message); }
    private String invitationHash() {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
