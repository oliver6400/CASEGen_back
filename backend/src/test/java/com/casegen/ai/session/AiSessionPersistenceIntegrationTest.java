package com.casegen.ai.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.casegen.ai.session.domain.AiSession;
import com.casegen.ai.session.domain.AiSessionParticipant;
import com.casegen.ai.session.domain.AiSessionStatus;
import com.casegen.ai.session.repository.AiSessionParticipantRepository;
import com.casegen.ai.session.repository.AiSessionRepository;
import com.casegen.auth.domain.User;
import com.casegen.auth.repository.UserRepository;
import com.casegen.project.domain.Project;
import com.casegen.project.repository.ProjectRepository;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = "jdbc:postgresql:.*")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AiSessionPersistenceIntegrationTest {

    private final AiSessionRepository sessions;
    private final AiSessionParticipantRepository participants;
    private final ProjectRepository projects;
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;

    AiSessionPersistenceIntegrationTest(AiSessionRepository sessions,
            AiSessionParticipantRepository participants, ProjectRepository projects,
            UserRepository users, JdbcTemplate jdbc, EntityManager entityManager) {
        this.sessions = sessions;
        this.participants = participants;
        this.projects = projects;
        this.users = users;
        this.jdbc = jdbc;
        this.entityManager = entityManager;
    }

    @Test
    void persistsSessionAndMultipleParticipants() {
        Fixture fixture = fixture("participants");
        User second = user("second");
        AiSession session = sessions.saveAndFlush(new AiSession(
                fixture.project(), fixture.host(), Instant.parse("2026-10-25T12:00:00Z")));

        participants.save(new AiSessionParticipant(session, fixture.host(),
                Instant.parse("2026-10-25T12:00:00Z")));
        participants.saveAndFlush(new AiSessionParticipant(session, second,
                Instant.parse("2026-10-25T12:05:00Z")));
        entityManager.clear();

        assertEquals(2, participants.findBySession_IdOrderByJoinedAtAsc(session.getId()).size());
        assertEquals(2, sessions.findByIdAndProjectId(session.getId(), fixture.project().getId())
                .orElseThrow().getParticipants().size());
    }

    @Test
    void projectCanKeepSeveralClosedSessions() {
        Fixture fixture = fixture("history");
        AiSession first = closedSession(fixture, "2026-10-25T08:00:00Z");
        AiSession second = closedSession(fixture, "2026-10-25T10:00:00Z");
        sessions.save(first);
        sessions.saveAndFlush(second);

        var history = sessions.findByProjectIdOrderByStartedAtDesc(fixture.project().getId());

        assertEquals(2, history.size());
        assertEquals(second.getStartedAt(), history.getFirst().getStartedAt());
    }

    @Test
    void partialUniqueIndexRejectsTwoActiveSessionsForOneProject() {
        Fixture fixture = fixture("same-project");
        sessions.saveAndFlush(new AiSession(fixture.project(), fixture.host(), Instant.now()));

        sessions.save(new AiSession(fixture.project(), fixture.host(), Instant.now().plusSeconds(1)));

        assertThrows(DataIntegrityViolationException.class, sessions::flush);
    }

    @Test
    void differentProjectsCanEachHaveAnActiveSession() {
        Fixture first = fixture("project-one");
        Fixture second = fixture("project-two");

        sessions.save(new AiSession(first.project(), first.host(), Instant.now()));
        sessions.saveAndFlush(new AiSession(second.project(), second.host(), Instant.now()));

        assertEquals(1, sessions.findByProjectIdOrderByStartedAtDesc(first.project().getId()).size());
        assertEquals(1, sessions.findByProjectIdOrderByStartedAtDesc(second.project().getId()).size());
    }

    @Test
    void databaseRejectsActiveSessionWithEndDate() {
        Fixture fixture = fixture("invalid-active");

        assertThrows(DataIntegrityViolationException.class, () -> insertRawSession(
                fixture, "ACTIVE", Instant.now()));
    }

    @Test
    void databaseRejectsClosedSessionWithoutEndDate() {
        Fixture fixture = fixture("invalid-closed");

        assertThrows(DataIntegrityViolationException.class, () -> insertRawSession(
                fixture, "CLOSED", null));
    }

    private AiSession closedSession(Fixture fixture, String startedAt) {
        Instant start = Instant.parse(startedAt);
        AiSession session = new AiSession(fixture.project(), fixture.host(), start);
        session.close(start.plusSeconds(3600));
        return session;
    }

    private void insertRawSession(Fixture fixture, String status, Instant endedAt) {
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO ai_sessions
                    (id, project_id, host_user_id, status, started_at, ended_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), fixture.project().getId(), fixture.host().getId(), status,
                Timestamp.from(now), endedAt == null ? null : Timestamp.from(endedAt),
                Timestamp.from(now), Timestamp.from(now));
    }

    private Fixture fixture(String name) {
        User host = user("host-" + name);
        Project project = projects.saveAndFlush(new Project("AI session " + name, null));
        return new Fixture(project, host);
    }

    private User user(String name) {
        return users.saveAndFlush(new User(
                name + "-" + UUID.randomUUID() + "@test.local", "hash", name));
    }

    private record Fixture(Project project, User host) {
    }
}
