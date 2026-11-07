package com.casegen.ai.proposal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.casegen.ai.message.domain.AiMessage;
import com.casegen.ai.message.domain.AiSkill;
import com.casegen.ai.message.repository.AiMessageRepository;
import com.casegen.ai.proposal.domain.AiProposal;
import com.casegen.ai.proposal.domain.AiProposalStatus;
import com.casegen.ai.proposal.domain.AiProposalType;
import com.casegen.ai.proposal.repository.AiProposalRepository;
import com.casegen.ai.proposal.realtime.AiProposalCreatedInternalEvent;
import com.casegen.ai.proposal.service.AiProposalPersistenceService;
import com.casegen.ai.proposal.service.GeneratedAiProposal;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.lang.reflect.Method;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Lock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@Transactional
@RecordApplicationEvents
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = "jdbc:postgresql:.*")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AiProposalPersistenceIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-27T12:00:00Z");
    private static final JsonNode PAYLOAD = JsonMapper.builder().build()
            .valueToTree(Map.of("name", "Order"));

    private final AiProposalRepository proposals;
    private final AiMessageRepository messages;
    private final AiSessionRepository sessions;
    private final UserRepository users;
    private final ProjectRepository projects;
    private final UmlModelRepository models;
    private final DiagramRepository diagrams;
    private final JdbcTemplate jdbc;
    private final AiProposalPersistenceService persistence;
    @Autowired
    private ApplicationEvents applicationEvents;

    AiProposalPersistenceIntegrationTest(AiProposalRepository proposals,
            AiMessageRepository messages, AiSessionRepository sessions, UserRepository users,
            ProjectRepository projects, UmlModelRepository models, DiagramRepository diagrams,
            JdbcTemplate jdbc, AiProposalPersistenceService persistence) {
        this.proposals = proposals;
        this.messages = messages;
        this.sessions = sessions;
        this.users = users;
        this.projects = projects;
        this.models = models;
        this.diagrams = diagrams;
        this.jdbc = jdbc;
        this.persistence = persistence;
    }

    @Test
    void v16CreatesAiProposalsTable() {
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = 'ai_proposals'
                """, Integer.class));
    }

    @Test
    void v17DecisionAuditColumnsRemainPresentAtCurrentFlywayVersion() {
        assertEquals(5, jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema='public' AND table_name='ai_proposals'
                  AND column_name IN ('decided_by_user_id', 'decided_at', 'decision_note',
                                      'applied_model_revision', 'applied_element_id')
                """, Integer.class));
        assertEquals("19", jdbc.queryForObject("""
                SELECT version FROM flyway_schema_history
                WHERE success = true ORDER BY installed_rank DESC LIMIT 1
                """, String.class));
    }

    @Test
    void existingPendingShapePersistsWithNullDecisionAudit() {
        AiProposal saved = save(fixture(false), UUID.randomUUID());
        Map<String, Object> audit = jdbc.queryForMap("""
                SELECT decided_by_user_id, decided_at, decision_note,
                       applied_model_revision, applied_element_id
                FROM ai_proposals WHERE id=?
                """, saved.getId());
        assertTrue(audit.values().stream().allMatch(java.util.Objects::isNull));
    }

    @Test
    void findForDecisionUsesPessimisticWriteAndScopesByProject() throws Exception {
        Fixture fixture = fixture(false);
        AiProposal saved = save(fixture, UUID.randomUUID());
        assertEquals(saved.getId(), proposals.findForDecision(
                fixture.project().getId(), saved.getId()).orElseThrow().getId());
        assertTrue(proposals.findForDecision(UUID.randomUUID(), saved.getId()).isEmpty());

        Method method = AiProposalRepository.class.getMethod(
                "findForDecision", UUID.class, UUID.class);
        assertEquals(LockModeType.PESSIMISTIC_WRITE, method.getAnnotation(Lock.class).value());
    }

    @Test
    void appliedDecisionPersistsCompleteAudit() {
        Fixture fixture = fixture(false);
        AiProposal saved = save(fixture, UUID.randomUUID());
        UUID elementId = UUID.randomUUID();
        saved.applyDecision(fixture.user(), NOW, " Applied ", 4L, elementId);
        proposals.saveAndFlush(saved);

        AiProposal reloaded = proposals.findById(saved.getId()).orElseThrow();
        assertEquals(AiProposalStatus.APPLIED, reloaded.getStatus());
        assertEquals(fixture.user().getId(), reloaded.getDecidedBy().getId());
        assertEquals(NOW, reloaded.getDecidedAt());
        assertEquals("Applied", reloaded.getDecisionNote());
        assertEquals(4L, reloaded.getAppliedModelRevision());
        assertEquals(elementId, reloaded.getAppliedElementId());
    }

    @Test
    void rejectedDecisionPersistsWithoutAppliedResult() {
        Fixture fixture = fixture(false);
        AiProposal saved = save(fixture, UUID.randomUUID());
        saved.rejectDecision(fixture.user(), NOW, null);
        proposals.saveAndFlush(saved);

        AiProposal reloaded = proposals.findById(saved.getId()).orElseThrow();
        assertEquals(AiProposalStatus.REJECTED, reloaded.getStatus());
        assertEquals(fixture.user().getId(), reloaded.getDecidedBy().getId());
        assertEquals(NOW, reloaded.getDecidedAt());
        assertNull(reloaded.getAppliedModelRevision());
        assertNull(reloaded.getAppliedElementId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"decided_by_user_id", "decided_at", "applied_model_revision",
            "applied_element_id"})
    void pendingRejectsEveryDecisionField(String column) {
        Fixture fixture = fixture(false);
        AiProposal saved = save(fixture, UUID.randomUUID());
        Object value = switch (column) {
            case "decided_by_user_id" -> fixture.user().getId();
            case "decided_at" -> Timestamp.from(NOW);
            case "applied_model_revision" -> 1L;
            case "applied_element_id" -> UUID.randomUUID();
            default -> throw new IllegalArgumentException(column);
        };
        assertConstraint(() -> jdbc.update(
                "UPDATE ai_proposals SET " + column + "=? WHERE id=?", value, saved.getId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing_actor", "missing_date", "applied_revision", "applied_element"})
    void rejectedStateEnforcesAuditAndForbidsAppliedResult(String scenario) {
        Fixture fixture = fixture(false);
        AiProposal saved = save(fixture, UUID.randomUUID());
        String assignments = switch (scenario) {
            case "missing_actor" -> "status='REJECTED', decided_at=clock_timestamp()";
            case "missing_date" -> "status='REJECTED', decided_by_user_id='" + fixture.user().getId() + "'";
            case "applied_revision" -> "status='REJECTED', decided_by_user_id='" + fixture.user().getId()
                    + "', decided_at=clock_timestamp(), applied_model_revision=1";
            case "applied_element" -> "status='REJECTED', decided_by_user_id='" + fixture.user().getId()
                    + "', decided_at=clock_timestamp(), applied_element_id='" + UUID.randomUUID() + "'";
            default -> throw new IllegalArgumentException(scenario);
        };
        assertConstraint(() -> jdbc.update(
                "UPDATE ai_proposals SET " + assignments + " WHERE id=?", saved.getId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing_actor", "missing_date", "missing_revision", "missing_element"})
    void appliedStateRequiresCompleteAuditAndResult(String scenario) {
        Fixture fixture = fixture(false);
        AiProposal saved = save(fixture, UUID.randomUUID());
        String actor = fixture.user().getId().toString();
        String element = UUID.randomUUID().toString();
        String assignments = switch (scenario) {
            case "missing_actor" -> "status='APPLIED', decided_at=clock_timestamp(), applied_model_revision=1, applied_element_id='" + element + "'";
            case "missing_date" -> "status='APPLIED', decided_by_user_id='" + actor + "', applied_model_revision=1, applied_element_id='" + element + "'";
            case "missing_revision" -> "status='APPLIED', decided_by_user_id='" + actor + "', decided_at=clock_timestamp(), applied_element_id='" + element + "'";
            case "missing_element" -> "status='APPLIED', decided_by_user_id='" + actor + "', decided_at=clock_timestamp(), applied_model_revision=1";
            default -> throw new IllegalArgumentException(scenario);
        };
        assertConstraint(() -> jdbc.update(
                "UPDATE ai_proposals SET " + assignments + " WHERE id=?", saved.getId()));
    }

    @Test
    void databaseRejectsOversizedNoteAndNegativeAppliedRevision() {
        Fixture fixture = fixture(false);
        AiProposal saved = save(fixture, UUID.randomUUID());
        assertConstraint(() -> jdbc.update(
                "UPDATE ai_proposals SET decision_note=? WHERE id=?", "x".repeat(1001), saved.getId()));
    }

    @Test
    void databaseRejectsNegativeAppliedRevision() {
        Fixture fixture = fixture(false);
        AiProposal saved = save(fixture, UUID.randomUUID());
        assertConstraint(() -> jdbc.update(
                "UPDATE ai_proposals SET applied_model_revision=-1 WHERE id=?", saved.getId()));
    }

    @Test
    void decidedByForeignKeyIsEnforcedAndRestrictsUserDeletion() {
        Fixture fixture = fixture(false);
        AiProposal saved = save(fixture, UUID.randomUUID());
        assertConstraint(() -> jdbc.update("""
                UPDATE ai_proposals
                SET status='REJECTED', decided_by_user_id=?, decided_at=clock_timestamp()
                WHERE id=?
                """, UUID.randomUUID(), saved.getId()));
    }

    @Test
    void decidedByUserDeletionIsRestricted() {
        Fixture fixture = fixture(false);
        User decider = users.saveAndFlush(new User(
                UUID.randomUUID() + "@test.local", "hash", "Decider"));
        AiProposal saved = save(fixture, UUID.randomUUID());
        saved.rejectDecision(decider, NOW, null);
        proposals.saveAndFlush(saved);

        assertConstraint(() -> jdbc.update("DELETE FROM users WHERE id=?", decider.getId()));
    }

    @Test
    void persistsPendingProposalAndRepositoryQueries() {
        Fixture fixture = fixture(true);
        UUID clientId = UUID.randomUUID();
        AiProposal saved = save(fixture, clientId);

        assertNotNull(saved.getId());
        assertEquals(AiProposalStatus.PENDING, saved.getStatus());
        assertEquals(saved.getId(), proposals.findByProject_IdAndClientProposalId(
                fixture.project().getId(), clientId).orElseThrow().getId());
        assertTrue(proposals.existsByProject_IdAndClientProposalId(fixture.project().getId(), clientId));
        assertEquals(1, proposals.findByProject_IdAndStatusOrderByCreatedAtDescIdDesc(
                fixture.project().getId(), AiProposalStatus.PENDING, PageRequest.of(0, 10))
                .getTotalElements());
    }

    @Test
    void persistencePublishesOneMinimalInternalEventForNewProposal() {
        Fixture fixture = fixture(true);
        GeneratedAiProposal generated = mock(GeneratedAiProposal.class);
        when(generated.proposalType()).thenReturn(AiProposalType.ADD_CLASSIFIER);
        when(generated.title()).thenReturn("Add Order");
        when(generated.rationale()).thenReturn("The domain requires an order");
        when(generated.canonicalPayload()).thenReturn(PAYLOAD);
        when(generated.baseModelRevision()).thenReturn(fixture.model().getRevision());
        when(generated.baseDiagramRevision()).thenReturn(fixture.diagram().getRevision());
        when(generated.provider()).thenReturn("GEMINI");
        when(generated.modelId()).thenReturn(fixture.model().getId());
        when(generated.diagramId()).thenReturn(fixture.diagram().getId());
        when(generated.sourceMessageId()).thenReturn(fixture.assistant().getId());

        AiProposal persisted = persistence.persist(fixture.project().getId(),
                fixture.session().getId(), fixture.assistant().getId(), fixture.user().getId(),
                UUID.randomUUID(), generated);

        var events = applicationEvents.stream(AiProposalCreatedInternalEvent.class).toList();
        assertEquals(1, events.size());
        assertEquals(fixture.project().getId(), events.getFirst().projectId());
        assertEquals(persisted.getId(), events.getFirst().proposalId());
        assertNotNull(events.getFirst().occurredAt());
    }

    @Test
    void projectForeignKeyIsEnforced() {
        Fixture fixture = fixture(false);
        AiProposal saved = save(fixture, UUID.randomUUID());
        assertConstraint(() -> jdbc.update("UPDATE ai_proposals SET project_id=? WHERE id=?",
                UUID.randomUUID(), saved.getId()));
    }

    @Test
    void sessionForeignKeyIsEnforced() {
        Fixture fixture = fixture(false);
        AiProposal saved = save(fixture, UUID.randomUUID());
        assertConstraint(() -> jdbc.update("UPDATE ai_proposals SET session_id=? WHERE id=?",
                UUID.randomUUID(), saved.getId()));
    }

    @Test
    void sourceMessageForeignKeyIsEnforced() {
        Fixture fixture = fixture(false);
        AiProposal saved = save(fixture, UUID.randomUUID());
        assertConstraint(() -> jdbc.update("UPDATE ai_proposals SET source_message_id=? WHERE id=?",
                UUID.randomUUID(), saved.getId()));
    }

    @Test
    void requestedByForeignKeyIsEnforced() {
        Fixture fixture = fixture(false);
        AiProposal saved = save(fixture, UUID.randomUUID());
        assertConstraint(() -> jdbc.update("UPDATE ai_proposals SET requested_by_user_id=? WHERE id=?",
                UUID.randomUUID(), saved.getId()));
    }

    @Test
    void modelAndDiagramAreNullable() {
        Fixture fixture = fixture(false);
        AiProposal saved = save(fixture, UUID.randomUUID());
        assertNull(saved.getModel());
        assertNull(saved.getDiagram());
    }

    @Test
    void jsonbPreservesObjectPayload() {
        Fixture fixture = fixture(false);
        AiProposal saved = save(fixture, UUID.randomUUID());
        assertEquals("Order", jdbc.queryForObject(
                "SELECT payload ->> 'name' FROM ai_proposals WHERE id=?",
                String.class, saved.getId()));
        assertEquals("object", jdbc.queryForObject(
                "SELECT jsonb_typeof(payload) FROM ai_proposals WHERE id=?",
                String.class, saved.getId()));
    }

    @Test
    void invalidStatusIsRejected() {
        assertUpdateRejected("status", "UNKNOWN");
    }

    @Test
    void invalidProposalTypeIsRejected() {
        assertUpdateRejected("proposal_type", "REMOVE_ELEMENT");
    }

    @Test
    void invalidSkillIsRejected() {
        assertUpdateRejected("skill", "UNKNOWN");
    }

    @Test
    void blankTitleIsRejected() {
        assertUpdateRejected("title", "   ");
    }

    @Test
    void blankRationaleIsRejected() {
        assertUpdateRejected("rationale", "   ");
    }

    @Test
    void negativeRevisionsAreRejected() {
        Fixture fixture = fixture(true);
        AiProposal saved = save(fixture, UUID.randomUUID());
        assertConstraint(() -> jdbc.update(
                "UPDATE ai_proposals SET base_model_revision=-1 WHERE id=?", saved.getId()));
    }

    @Test
    void negativeDiagramRevisionIsRejected() {
        Fixture fixture = fixture(true);
        AiProposal saved = save(fixture, UUID.randomUUID());
        assertConstraint(() -> jdbc.update(
                "UPDATE ai_proposals SET base_diagram_revision=-1 WHERE id=?", saved.getId()));
    }

    @Test
    void zeroPayloadSchemaVersionIsRejected() {
        Fixture fixture = fixture(false);
        AiProposal saved = save(fixture, UUID.randomUUID());
        assertConstraint(() -> jdbc.update(
                "UPDATE ai_proposals SET payload_schema_version=0 WHERE id=?", saved.getId()));
    }

    @Test
    void nonObjectPayloadIsRejected() {
        Fixture fixture = fixture(false);
        AiProposal saved = save(fixture, UUID.randomUUID());
        assertConstraint(() -> jdbc.update(
                "UPDATE ai_proposals SET payload='[]'::jsonb WHERE id=?", saved.getId()));
    }

    @Test
    void duplicateClientProposalIdInsideProjectIsRejected() {
        Fixture fixture = fixture(false);
        UUID clientId = UUID.randomUUID();
        save(fixture, clientId);
        AiProposal duplicate = proposal(fixture, clientId);
        assertConstraint(() -> proposals.saveAndFlush(duplicate));
    }

    @Test
    void sameClientProposalIdInAnotherProjectIsAllowed() {
        UUID clientId = UUID.randomUUID();
        AiProposal first = save(fixture(false), clientId);
        AiProposal second = save(fixture(false), clientId);
        assertNotNull(first.getId());
        assertNotNull(second.getId());
    }

    @Test
    void deletingProjectCascadesProposal() {
        Fixture fixture = fixture(false);
        AiProposal saved = save(fixture, UUID.randomUUID());
        jdbc.update("DELETE FROM projects WHERE id=?", fixture.project().getId());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM ai_proposals WHERE id=?",
                Integer.class, saved.getId()));
    }

    @Test
    void deletingSessionIsRestricted() {
        Fixture fixture = fixture(false);
        save(fixture, UUID.randomUUID());
        assertConstraint(() -> jdbc.update("DELETE FROM ai_sessions WHERE id=?",
                fixture.session().getId()));
    }

    @Test
    void deletingSourceMessageIsRestricted() {
        Fixture fixture = fixture(false);
        save(fixture, UUID.randomUUID());
        assertConstraint(() -> jdbc.update("DELETE FROM ai_messages WHERE id=?",
                fixture.assistant().getId()));
    }

    @Test
    void deletingModelSetsProposalContextToNull() {
        Fixture fixture = fixture(true);
        AiProposal saved = save(fixture, UUID.randomUUID());
        jdbc.update("DELETE FROM uml_models WHERE id=?", fixture.model().getId());
        assertNull(jdbc.queryForObject("SELECT model_id FROM ai_proposals WHERE id=?",
                UUID.class, saved.getId()));
        assertNull(jdbc.queryForObject("SELECT diagram_id FROM ai_proposals WHERE id=?",
                UUID.class, saved.getId()));
    }

    @Test
    void deletingDiagramSetsProposalContextToNull() {
        Fixture fixture = fixture(true);
        AiProposal saved = save(fixture, UUID.randomUUID());
        jdbc.update("DELETE FROM diagrams WHERE id=?", fixture.diagram().getId());
        assertNull(jdbc.queryForObject("SELECT diagram_id FROM ai_proposals WHERE id=?",
                UUID.class, saved.getId()));
        assertEquals(fixture.model().getId(), jdbc.queryForObject(
                "SELECT model_id FROM ai_proposals WHERE id=?", UUID.class, saved.getId()));
    }

    @Test
    void persistingProposalDoesNotAlterExistingMessages() {
        Fixture fixture = fixture(false);
        int before = jdbc.queryForObject("SELECT count(*) FROM ai_messages WHERE session_id=?",
                Integer.class, fixture.session().getId());
        save(fixture, UUID.randomUUID());
        assertEquals(before, jdbc.queryForObject(
                "SELECT count(*) FROM ai_messages WHERE session_id=?", Integer.class,
                fixture.session().getId()));
    }

    private void assertUpdateRejected(String column, String value) {
        Fixture fixture = fixture(false);
        AiProposal saved = save(fixture, UUID.randomUUID());
        assertConstraint(() -> jdbc.update("UPDATE ai_proposals SET " + column + "=? WHERE id=?",
                value, saved.getId()));
    }

    private void assertConstraint(Runnable operation) {
        assertThrows(DataIntegrityViolationException.class, operation::run);
    }

    private AiProposal save(Fixture fixture, UUID clientId) {
        return proposals.saveAndFlush(proposal(fixture, clientId));
    }

    private AiProposal proposal(Fixture fixture, UUID clientId) {
        Long modelRevision = fixture.model() == null ? null : fixture.model().getRevision();
        Long diagramRevision = fixture.diagram() == null ? null : fixture.diagram().getRevision();
        return AiProposal.pending(fixture.project(), fixture.session(), fixture.assistant(),
                fixture.user(), fixture.model(), fixture.diagram(), AiProposalType.ADD_CLASSIFIER,
                "Add Order", "The domain requires an order", PAYLOAD, (short) 1,
                modelRevision, diagramRevision, "GEMINI", clientId);
    }

    private Fixture fixture(boolean withContext) {
        String suffix = UUID.randomUUID().toString();
        User user = users.saveAndFlush(new User(suffix + "@test.local", "hash", "Member"));
        Project project = projects.saveAndFlush(new Project("AI proposal " + suffix, null));
        AiSession session = sessions.saveAndFlush(new AiSession(project, user, NOW));
        UmlModel model = withContext
                ? models.saveAndFlush(new UmlModel(project, "Model " + suffix, null, user)) : null;
        Diagram diagram = withContext
                ? diagrams.saveAndFlush(new Diagram(model, null, "Diagram " + suffix,
                        DiagramType.CLASS, user)) : null;
        AiMessage question = messages.saveAndFlush(AiMessage.userMessage(session, user,
                "Suggest a UML change", model, diagram, UUID.randomUUID(), AiSkill.UML_REVIEWER, NOW));
        AiMessage assistant = messages.saveAndFlush(AiMessage.assistantMessage(session,
                "Add Order", "GEMINI", model, diagram, question, NOW.plusSeconds(1)));
        return new Fixture(user, project, session, model, diagram, assistant);
    }

    private record Fixture(User user, Project project, AiSession session, UmlModel model,
                           Diagram diagram, AiMessage assistant) { }
}
