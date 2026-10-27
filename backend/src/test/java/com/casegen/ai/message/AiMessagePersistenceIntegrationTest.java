package com.casegen.ai.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.casegen.ai.message.domain.AiMessage;
import com.casegen.ai.message.repository.AiMessageRepository;
import com.casegen.ai.session.domain.AiSession;
import com.casegen.ai.session.repository.AiSessionRepository;
import com.casegen.auth.domain.User;
import com.casegen.auth.repository.UserRepository;
import com.casegen.modeling.domain.Diagram;
import com.casegen.modeling.domain.DiagramType;
import com.casegen.modeling.domain.UmlModel;
import com.casegen.modeling.repository.DiagramRepository;
import com.casegen.modeling.repository.UmlModelRepository;
import com.casegen.project.domain.Project;
import com.casegen.project.repository.ProjectRepository;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = "jdbc:postgresql:.*")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AiMessagePersistenceIntegrationTest {

    private static final Instant BASE_TIME = Instant.parse("2026-10-25T12:00:00Z");

    private final AiMessageRepository messages;
    private final AiSessionRepository sessions;
    private final UserRepository users;
    private final ProjectRepository projects;
    private final UmlModelRepository models;
    private final DiagramRepository diagrams;
    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;

    AiMessagePersistenceIntegrationTest(AiMessageRepository messages, AiSessionRepository sessions,
            UserRepository users, ProjectRepository projects, UmlModelRepository models,
            DiagramRepository diagrams, JdbcTemplate jdbc, EntityManager entityManager) {
        this.messages = messages;
        this.sessions = sessions;
        this.users = users;
        this.projects = projects;
        this.models = models;
        this.diagrams = diagrams;
        this.jdbc = jdbc;
        this.entityManager = entityManager;
    }

    @Test
    void persistsUserAndAssistantAndReturnsStableHistory() {
        Fixture fixture = fixture("history");
        UUID clientId = UUID.randomUUID();
        AiMessage user = messages.saveAndFlush(AiMessage.userMessage(fixture.session(),
                fixture.user(), "First", null, null, clientId, BASE_TIME));
        messages.saveAndFlush(AiMessage.assistantMessage(fixture.session(), "Second", "ollama",
                null, null, user, BASE_TIME.plusSeconds(1)));
        entityManager.clear();

        var history = messages.findBySession_IdOrderByCreatedAtAscIdAsc(
                fixture.session().getId(), PageRequest.of(0, 20));

        assertEquals(2, history.getTotalElements());
        assertEquals("First", history.getContent().getFirst().getContent());
        assertEquals("Second", history.getContent().get(1).getContent());
        assertEquals(clientId, messages.findBySession_IdAndClientMessageId(
                fixture.session().getId(), clientId).orElseThrow().getClientMessageId());
        assertNotNull(messages.findByReplyTo_IdAndRole(user.getId(),
                com.casegen.ai.message.domain.AiMessageRole.ASSISTANT).orElseThrow());
    }

    @Test
    void supportsOptionalModelAndDiagramContext() {
        Fixture fixture = fixture("context");
        UmlModel model = models.saveAndFlush(new UmlModel(
                fixture.project(), "Domain", null, fixture.user()));
        Diagram diagram = diagrams.saveAndFlush(new Diagram(
                model, null, "Classes", DiagramType.CLASS, fixture.user()));

        AiMessage saved = messages.saveAndFlush(AiMessage.userMessage(fixture.session(),
                fixture.user(), "Inspect diagram", model, diagram, UUID.randomUUID(), BASE_TIME));
        entityManager.clear();
        AiMessage reloaded = messages.findById(saved.getId()).orElseThrow();

        assertEquals(model.getId(), reloaded.getModel().getId());
        assertEquals(diagram.getId(), reloaded.getDiagram().getId());
    }

    @Test
    void differentWorkSessionsKeepIndependentHistoriesAndClientIds() {
        Fixture first = fixture("day-one");
        first.session().close(BASE_TIME.plusSeconds(3600));
        sessions.saveAndFlush(first.session());
        AiSession secondSession = sessions.saveAndFlush(
                new AiSession(first.project(), first.user(), BASE_TIME.plusSeconds(86400)));
        UUID repeatedClientId = UUID.randomUUID();
        messages.save(AiMessage.userMessage(first.session(), first.user(), "Day one",
                null, null, repeatedClientId, BASE_TIME));
        messages.saveAndFlush(AiMessage.userMessage(secondSession, first.user(), "Day two",
                null, null, repeatedClientId, BASE_TIME.plusSeconds(86400)));

        assertEquals(1, messages.findBySession_IdOrderByCreatedAtAscIdAsc(
                first.session().getId(), PageRequest.of(0, 10)).getTotalElements());
        assertEquals(1, messages.findBySession_IdOrderByCreatedAtAscIdAsc(
                secondSession.getId(), PageRequest.of(0, 10)).getTotalElements());
    }

    @Test
    void deletingSessionCascadesItsMessages() {
        Fixture fixture = fixture("cascade");
        messages.saveAndFlush(AiMessage.userMessage(fixture.session(), fixture.user(), "Question",
                null, null, UUID.randomUUID(), BASE_TIME));
        UUID sessionId = fixture.session().getId();
        entityManager.clear();

        jdbc.update("DELETE FROM ai_sessions WHERE id = ?", sessionId);

        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM ai_messages WHERE session_id = ?", Integer.class, sessionId));
    }

    @Test
    void databaseRejectsBlankContentAndInvalidRole() {
        Fixture fixture = fixture("checks");

        assertThrows(DataIntegrityViolationException.class,
                () -> insertRaw(fixture, "USER", " ", fixture.user().getId(), null,
                        UUID.randomUUID(), null));
    }

    @Test
    void databaseRejectsInvalidRole() {
        Fixture fixture = fixture("invalid-role");

        assertThrows(DataIntegrityViolationException.class,
                () -> insertRaw(fixture, "SYSTEM", "Internal", fixture.user().getId(), null,
                        UUID.randomUUID(), null));
    }

    @Test
    void databaseRejectsDuplicateClientMessageWithinSession() {
        Fixture fixture = fixture("idempotency");
        UUID clientId = UUID.randomUUID();
        messages.saveAndFlush(AiMessage.userMessage(fixture.session(), fixture.user(), "First",
                null, null, clientId, BASE_TIME));

        assertThrows(DataIntegrityViolationException.class, () -> {
            messages.save(AiMessage.userMessage(fixture.session(), fixture.user(), "Retry",
                    null, null, clientId, BASE_TIME.plusSeconds(1)));
            messages.flush();
        });
    }

    @Test
    void databaseAllowsOnlyOneAssistantReplyPerUserMessage() {
        Fixture fixture = fixture("single-reply");
        AiMessage user = messages.saveAndFlush(AiMessage.userMessage(fixture.session(),
                fixture.user(), "Question", null, null, UUID.randomUUID(), BASE_TIME));
        messages.saveAndFlush(AiMessage.assistantMessage(fixture.session(), "First", "ollama",
                null, null, user, BASE_TIME.plusSeconds(1)));

        assertThrows(DataIntegrityViolationException.class, () -> {
            messages.save(AiMessage.assistantMessage(fixture.session(), "Second", "gemini",
                    null, null, user, BASE_TIME.plusSeconds(2)));
            messages.flush();
        });
    }

    @Test
    void databaseAllowsSeveralAssistantMessagesWithoutReply() {
        Fixture fixture = fixture("assistant-notices");
        messages.save(AiMessage.assistantMessage(fixture.session(), "First", "ollama",
                null, null, null, BASE_TIME));
        messages.saveAndFlush(AiMessage.assistantMessage(fixture.session(), "Second", "ollama",
                null, null, null, BASE_TIME.plusSeconds(1)));

        assertEquals(2, messages.findBySession_IdOrderByCreatedAtAscIdAsc(
                fixture.session().getId(), PageRequest.of(0, 10)).getTotalElements());
    }

    @Test
    void authorForeignKeyRejectsUnknownUser() {
        Fixture fixture = fixture("author-fk");

        assertThrows(DataIntegrityViolationException.class,
                () -> insertRaw(fixture, "USER", "Question", UUID.randomUUID(), null,
                        UUID.randomUUID(), null));
    }

    @Test
    void legacyInsertWithoutSkillUsesGeneralMigrationDefault() {
        Fixture fixture = fixture("legacy-skill-default");
        UUID clientMessageId = UUID.randomUUID();

        insertRaw(fixture, "USER", "Legacy question", fixture.user().getId(), null,
                clientMessageId, null);

        assertEquals("GENERAL", jdbc.queryForObject(
                "SELECT skill FROM ai_messages WHERE session_id = ? AND client_message_id = ?",
                String.class, fixture.session().getId(), clientMessageId));
    }

    @Test
    void recentProjectHistorySpansSessionsRespectsLimitAndExcludesOtherProjects() {
        Fixture projectA = fixture("context-project-a");
        projectA.session().close(BASE_TIME.plusSeconds(60));
        sessions.saveAndFlush(projectA.session());
        AiSession secondDay = sessions.saveAndFlush(new AiSession(
                projectA.project(), projectA.user(), BASE_TIME.plusSeconds(120)));
        Fixture projectB = fixture("context-project-b");
        messages.save(AiMessage.userMessage(projectA.session(), projectA.user(), "A1",
                null, null, UUID.randomUUID(), BASE_TIME));
        messages.save(AiMessage.userMessage(secondDay, projectA.user(), "A2",
                null, null, UUID.randomUUID(), BASE_TIME.plusSeconds(120)));
        messages.save(AiMessage.assistantMessage(secondDay, "A3", "ollama",
                null, null, null, BASE_TIME.plusSeconds(180)));
        messages.saveAndFlush(AiMessage.userMessage(projectB.session(), projectB.user(), "B1",
                null, null, UUID.randomUUID(), BASE_TIME.plusSeconds(240)));

        var recent = messages.findRecentByProjectId(
                projectA.project().getId(), PageRequest.of(0, 2));

        assertEquals(List.of("A3", "A2"), recent.stream().map(AiMessage::getContent).toList());
    }

    private void insertRaw(Fixture fixture, String role, String content, UUID authorId,
            String provider, UUID clientMessageId, UUID replyToId) {
        jdbc.update("""
                INSERT INTO ai_messages
                    (id, session_id, author_user_id, role, content, provider,
                     client_message_id, reply_to_message_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), fixture.session().getId(), authorId, role, content,
                provider, clientMessageId, replyToId, Timestamp.from(BASE_TIME));
    }

    private Fixture fixture(String name) {
        User user = users.saveAndFlush(new User(
                name + "-" + UUID.randomUUID() + "@test.local", "hash", name));
        Project project = projects.saveAndFlush(new Project("AI messages " + name, null));
        AiSession session = sessions.saveAndFlush(new AiSession(project, user, BASE_TIME));
        return new Fixture(user, project, session);
    }

    private record Fixture(User user, Project project, AiSession session) {
    }
}
