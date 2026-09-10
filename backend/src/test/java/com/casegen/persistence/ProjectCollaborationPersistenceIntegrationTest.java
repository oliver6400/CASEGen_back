package com.casegen.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.casegen.auth.domain.SystemRole;
import com.casegen.auth.domain.User;
import com.casegen.auth.repository.UserRepository;
import com.casegen.project.domain.Project;
import com.casegen.project.domain.ProjectInvitation;
import com.casegen.project.domain.ProjectInvitationRole;
import com.casegen.project.domain.ProjectMember;
import com.casegen.project.domain.ProjectMemberRole;
import com.casegen.project.repository.ProjectInvitationRepository;
import com.casegen.project.repository.ProjectMemberRepository;
import com.casegen.project.repository.ProjectRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = "jdbc:postgresql:.*")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ProjectCollaborationPersistenceIntegrationTest {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectInvitationRepository projectInvitationRepository;
    private final JdbcTemplate jdbcTemplate;

    ProjectCollaborationPersistenceIntegrationTest(UserRepository userRepository,
            ProjectRepository projectRepository, ProjectMemberRepository projectMemberRepository,
            ProjectInvitationRepository projectInvitationRepository, JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.projectInvitationRepository = projectInvitationRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void persistsUserWithDefaultSystemRole() {
        User user = persistUser("owner@example.com");

        assertNotNull(user.getId());
        assertEquals(SystemRole.USER, user.getSystemRole());
    }

    @Test
    void rejectsCaseInsensitiveDuplicateUserEmail() {
        persistUser("owner@example.com");

        assertThrows(DataIntegrityViolationException.class,
                () -> userRepository.saveAndFlush(new User("OWNER@example.com", "hash-2", "Second owner")));
    }

    @Test
    void rejectsInvalidSystemRole() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO users (id, email, password_hash, display_name, system_role, enabled, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), "invalid-role@example.com", "hash", "Invalid role", "INVALID", true,
                sqlNow(), sqlNow()));
    }

    @Test
    void persistsProjectMemberForExistingProjectAndUser() {
        Project project = persistProject();
        User user = persistUser("member@example.com");

        ProjectMember member = projectMemberRepository.saveAndFlush(
                new ProjectMember(project, user, ProjectMemberRole.COLLABORATOR));

        assertNotNull(member.getId());
        assertEquals(ProjectMemberRole.COLLABORATOR, member.getRole());
    }

    @Test
    void rejectsSecondActiveOwnerForSameProject() {
        Project project = persistProject();
        User firstOwner = persistUser("first-owner@example.com");
        User secondOwner = persistUser("second-owner@example.com");
        projectMemberRepository.saveAndFlush(
                new ProjectMember(project, firstOwner, ProjectMemberRole.OWNER));

        assertThrows(DataIntegrityViolationException.class, () -> projectMemberRepository.saveAndFlush(
                new ProjectMember(project, secondOwner, ProjectMemberRole.OWNER)));
    }

    @Test
    void rejectsDuplicateProjectMember() {
        Project project = persistProject();
        User user = persistUser("member@example.com");
        projectMemberRepository.saveAndFlush(new ProjectMember(project, user, ProjectMemberRole.COLLABORATOR));

        assertThrows(DataIntegrityViolationException.class, () -> projectMemberRepository.saveAndFlush(
                new ProjectMember(project, user, ProjectMemberRole.VIEWER)));
    }

    @Test
    void rejectsProjectMemberWithUnknownProject() {
        User user = persistUser("member@example.com");

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO project_members (id, project_id, user_id, role, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), UUID.randomUUID(), user.getId(), "COLLABORATOR", "ACTIVE", sqlNow()));
    }

    @Test
    void rejectsProjectMemberWithUnknownUser() {
        Project project = persistProject();

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO project_members (id, project_id, user_id, role, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), project.getId(), UUID.randomUUID(), "COLLABORATOR", "ACTIVE", sqlNow()));
    }

    @Test
    void rejectsInvalidProjectMemberRole() {
        Project project = persistProject();
        User user = persistUser("member@example.com");

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO project_members (id, project_id, user_id, role, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), project.getId(), user.getId(), "INVALID", "ACTIVE", sqlNow()));
    }

    @Test
    void persistsInvitationForExistingProjectAndInviter() {
        Project project = persistProject();
        User inviter = persistUser("owner@example.com");

        ProjectInvitation invitation = projectInvitationRepository.saveAndFlush(new ProjectInvitation(project, inviter,
                "invitee@example.com", ProjectInvitationRole.COLLABORATOR, "token-hash-1",
                Instant.now().plus(7, ChronoUnit.DAYS)));

        assertNotNull(invitation.getId());
        assertEquals(ProjectInvitationRole.COLLABORATOR, invitation.getRole());
    }

    @Test
    void rejectsDuplicateInvitationTokenHash() {
        Project project = persistProject();
        User inviter = persistUser("owner@example.com");
        projectInvitationRepository.saveAndFlush(new ProjectInvitation(project, inviter, "first@example.com",
                ProjectInvitationRole.COLLABORATOR, "duplicate-token", Instant.now().plus(7, ChronoUnit.DAYS)));

        assertThrows(DataIntegrityViolationException.class, () -> projectInvitationRepository.saveAndFlush(
                new ProjectInvitation(project, inviter, "second@example.com", ProjectInvitationRole.VIEWER,
                        "duplicate-token", Instant.now().plus(7, ChronoUnit.DAYS))));
    }

    @Test
    void rejectsInvitationWithUnknownProject() {
        User inviter = persistUser("owner@example.com");

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO project_invitations (id, project_id, invited_by, email, role, token_hash, status, expires_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), UUID.randomUUID(), inviter.getId(), "invitee@example.com", "VIEWER",
                "unknown-project-token", "PENDING", sqlNow().plusDays(7), sqlNow()));
    }

    @Test
    void rejectsInvitationWithUnknownInviter() {
        Project project = persistProject();

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO project_invitations (id, project_id, invited_by, email, role, token_hash, status, expires_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), project.getId(), UUID.randomUUID(), "invitee@example.com", "VIEWER",
                "unknown-inviter-token", "PENDING", sqlNow().plusDays(7), sqlNow()));
    }

    @Test
    void rejectsHostAsInvitationRole() {
        Project project = persistProject();
        User inviter = persistUser("owner@example.com");

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO project_invitations (id, project_id, invited_by, email, role, token_hash, status, expires_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), project.getId(), inviter.getId(), "invitee@example.com", "HOST",
                "host-token", "PENDING", sqlNow().plusDays(7), sqlNow()));
    }

    private User persistUser(String email) {
        return userRepository.saveAndFlush(new User(email, "password-hash", "CASEGen user"));
    }

    private Project persistProject() {
        return projectRepository.saveAndFlush(new Project("CASEGen project", "Persistence test"));
    }

    private OffsetDateTime sqlNow() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
