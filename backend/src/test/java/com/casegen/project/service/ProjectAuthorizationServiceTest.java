package com.casegen.project.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.casegen.auth.domain.User;
import com.casegen.auth.service.CurrentUserService;
import com.casegen.project.domain.Project;
import com.casegen.project.domain.ProjectMember;
import com.casegen.project.domain.ProjectMemberRole;
import com.casegen.project.domain.ProjectMemberStatus;
import com.casegen.project.repository.ProjectMemberRepository;
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
    private User user;
    private Project project;

    @BeforeEach
    void setUp() {
        currentUserService = Mockito.mock(CurrentUserService.class);
        memberRepository = Mockito.mock(ProjectMemberRepository.class);
        authorizationService = new ProjectAuthorizationService(currentUserService, memberRepository);
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

    private void membership(ProjectMemberRole role) {
        when(memberRepository.findByProjectIdAndUserIdAndStatus(
                project.getId(), user.getId(), ProjectMemberStatus.ACTIVE))
                .thenReturn(Optional.of(new ProjectMember(project, user, role)));
    }
}
