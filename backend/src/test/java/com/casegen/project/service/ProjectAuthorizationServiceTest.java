package com.casegen.project.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.casegen.auth.domain.User;
import com.casegen.auth.repository.UserRepository;
import com.casegen.auth.service.CurrentUserService;
import com.casegen.project.domain.Project;
import com.casegen.project.domain.ProjectMember;
import com.casegen.project.domain.ProjectMemberRole;
import com.casegen.project.domain.ProjectMemberStatus;
import com.casegen.project.repository.ProjectMemberRepository;
import com.casegen.project.repository.ProjectRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;

class ProjectAuthorizationServiceTest {

    private CurrentUserService currentUserService;
    private ProjectMemberRepository memberRepository;
    private ProjectAuthorizationService authorizationService;
    private ProjectRepository projectRepository;
    private UserRepository userRepository;
    private User user;
    private Project project;

    @BeforeEach
    void setUp() {
        currentUserService = Mockito.mock(CurrentUserService.class);
        memberRepository = Mockito.mock(ProjectMemberRepository.class);
        projectRepository = Mockito.mock(ProjectRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        authorizationService = new ProjectAuthorizationService(currentUserService, memberRepository,
                projectRepository, userRepository);
        user = Mockito.mock(User.class);
        project = Mockito.mock(Project.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        when(project.getId()).thenReturn(UUID.randomUUID());
        when(currentUserService.requireCurrentUser()).thenReturn(user);
    }

    @Test
    void ownerCanManageMembers() {
        membership(ProjectMemberRole.OWNER);

        assertDoesNotThrow(() -> authorizationService.requireManageMembers(project.getId()));
    }

    @Test
    void collaboratorCannotManageMembers() {
        membership(ProjectMemberRole.COLLABORATOR);

        assertThrows(AccessDeniedException.class,
                () -> authorizationService.requireManageMembers(project.getId()));
    }

    @Test
    void privateProjectRejectsUnrelatedWebSocketPrincipal() {
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(memberRepository.findByProjectIdAndUserIdAndStatus(
                project.getId(), user.getId(), ProjectMemberStatus.ACTIVE)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertFalse(
                authorizationService.canReadAs(project.getId(), user.getId()));
    }

    @Test
    void publicProjectAllowsAnonymousReadSubscription() {
        when(project.getVisibility()).thenReturn(com.casegen.project.domain.ProjectVisibility.PUBLIC);
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));

        org.junit.jupiter.api.Assertions.assertTrue(
                authorizationService.canReadAs(project.getId(), null));
    }

    @Test
    void viewerCannotPublishEditingPresence() {
        when(user.isEnabled()).thenReturn(true);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        membership(ProjectMemberRole.VIEWER);

        org.junit.jupiter.api.Assertions.assertFalse(
                authorizationService.canWriteAs(project.getId(), user.getId()));
    }

    private void membership(ProjectMemberRole role) {
        when(memberRepository.findByProjectIdAndUserIdAndStatus(
                project.getId(), user.getId(), ProjectMemberStatus.ACTIVE))
                .thenReturn(Optional.of(new ProjectMember(project, user, role)));
    }
}
