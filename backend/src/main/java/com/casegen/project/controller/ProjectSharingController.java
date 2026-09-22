package com.casegen.project.controller;

import com.casegen.project.dto.ProjectSharingDtos.*;
import com.casegen.project.service.ProjectSharingService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class ProjectSharingController {
    private final ProjectSharingService service;
    public ProjectSharingController(ProjectSharingService service) { this.service=service; }
    @PatchMapping("/api/projects/{projectId}/visibility") public VisibilityResponse visibility(@PathVariable UUID projectId,@Valid @RequestBody VisibilityRequest request) { return service.visibility(projectId,request); }
    @GetMapping("/api/projects/{projectId}/members") public List<MemberResponse> members(@PathVariable UUID projectId) { return service.members(projectId); }
    @PatchMapping("/api/projects/{projectId}/members/{memberId}/role") public MemberResponse role(@PathVariable UUID projectId,@PathVariable UUID memberId,@Valid @RequestBody MemberRoleRequest request) { return service.changeRole(projectId,memberId,request); }
    @DeleteMapping("/api/projects/{projectId}/members/{memberId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void revokeMember(@PathVariable UUID projectId,@PathVariable UUID memberId) { service.revokeMember(projectId,memberId); }
    @PostMapping("/api/projects/{projectId}/invitations") @ResponseStatus(HttpStatus.CREATED) public InvitationResponse invite(@PathVariable UUID projectId,@Valid @RequestBody InviteRequest request) { return service.invite(projectId,request); }
    @GetMapping("/api/projects/{projectId}/invitations") public List<InvitationResponse> projectInvitations(@PathVariable UUID projectId) { return service.projectInvitations(projectId); }
    @GetMapping("/api/me/project-invitations") public List<InvitationResponse> mine() { return service.mine(); }
    @PostMapping("/api/project-invitations/{invitationId}/accept") public InvitationResponse accept(@PathVariable UUID invitationId) { return service.accept(invitationId); }
    @PostMapping("/api/project-invitations/{invitationId}/reject") public InvitationResponse reject(@PathVariable UUID invitationId) { return service.reject(invitationId); }
    @PostMapping("/api/projects/{projectId}/invitations/{invitationId}/revoke") public InvitationResponse revoke(@PathVariable UUID projectId,@PathVariable UUID invitationId) { return service.revokeInvitation(projectId,invitationId); }
}
