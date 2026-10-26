package com.casegen.ai.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.casegen.ai.session.domain.AiSession;
import com.casegen.ai.session.domain.AiSessionStatus;
import com.casegen.ai.session.repository.AiSessionParticipantRepository;
import com.casegen.ai.session.repository.AiSessionRepository;
import com.casegen.ai.session.service.AiSessionService;
import com.casegen.auth.domain.User;
import com.casegen.auth.repository.UserRepository;
import com.casegen.project.domain.Project;
import com.casegen.project.domain.ProjectMember;
import com.casegen.project.domain.ProjectMemberRole;
import com.casegen.project.repository.ProjectMemberRepository;
import com.casegen.project.repository.ProjectRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = "jdbc:postgresql:.*")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AiSessionConcurrencyIntegrationTest {
    private final AiSessionService service;
    private final AiSessionRepository sessions;
    private final AiSessionParticipantRepository participants;
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final UserRepository users;
    private final TransactionTemplate transactions;
    private final List<UUID> createdUsers = new ArrayList<>();
    private UUID createdProject;

    AiSessionConcurrencyIntegrationTest(AiSessionService service, AiSessionRepository sessions,
            AiSessionParticipantRepository participants, ProjectRepository projects,
            ProjectMemberRepository members, UserRepository users,
            PlatformTransactionManager transactionManager) {
        this.service = service;
        this.sessions = sessions;
        this.participants = participants;
        this.projects = projects;
        this.members = members;
        this.users = users;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void cleanUp() {
        if (createdProject == null) return;
        transactions.executeWithoutResult(status -> {
            members.deleteAll(members.findByProjectId(createdProject));
            members.flush();
            projects.findById(createdProject).ifPresent(projects::delete);
        });
        transactions.executeWithoutResult(status -> users.deleteAllById(createdUsers));
        createdProject = null;
        createdUsers.clear();
    }

    @Test
    void concurrentStartsReuseOneActiveSessionAndRegisterBothParticipants() throws Exception {
        Fixture fixture = transactions.execute(status -> fixture());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var ownerStart = executor.submit(() -> start(fixture.projectId(), fixture.ownerId(), ready, start));
            var collaboratorStart = executor.submit(
                    () -> start(fixture.projectId(), fixture.collaboratorId(), ready, start));
            ready.await();
            start.countDown();

            UUID ownerResult = ownerStart.get();
            UUID collaboratorResult = collaboratorStart.get();

            assertEquals(ownerResult, collaboratorResult);
            transactions.executeWithoutResult(status -> {
                List<AiSession> active = sessions.findByProjectIdOrderByStartedAtDesc(fixture.projectId())
                        .stream().filter(session -> session.getStatus() == AiSessionStatus.ACTIVE).toList();
                assertEquals(1, active.size());
                assertTrue(participants.existsBySession_IdAndUser_Id(active.getFirst().getId(), fixture.ownerId()));
                assertTrue(participants.existsBySession_IdAndUser_Id(
                        active.getFirst().getId(), fixture.collaboratorId()));
            });
        }
    }

    private UUID start(UUID projectId, UUID userId, CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        try {
            return transactions.execute(status -> {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(userId.toString(), "", List.of()));
                return service.startOrReuse(projectId).session().getId();
            });
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Fixture fixture() {
        User owner = users.save(new User(email("owner"), "hash", "Concurrent owner"));
        User collaborator = users.save(new User(email("collaborator"), "hash", "Concurrent collaborator"));
        Project project = projects.save(new Project("Concurrent AI session " + UUID.randomUUID(), null));
        members.save(new ProjectMember(project, owner, ProjectMemberRole.OWNER));
        members.save(new ProjectMember(project, collaborator, ProjectMemberRole.COLLABORATOR));
        users.flush();
        projects.flush();
        members.flush();
        createdProject = project.getId();
        createdUsers.add(owner.getId());
        createdUsers.add(collaborator.getId());
        return new Fixture(project.getId(), owner.getId(), collaborator.getId());
    }

    private String email(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@test.local";
    }

    private record Fixture(UUID projectId, UUID ownerId, UUID collaboratorId) {
    }
}
