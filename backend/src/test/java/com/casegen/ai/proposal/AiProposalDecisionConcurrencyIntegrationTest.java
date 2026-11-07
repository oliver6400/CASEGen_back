package com.casegen.ai.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.casegen.ai.message.domain.AiMessage;
import com.casegen.ai.message.domain.AiSkill;
import com.casegen.ai.message.repository.AiMessageRepository;
import com.casegen.ai.proposal.domain.AiProposal;
import com.casegen.ai.proposal.domain.AiProposalStatus;
import com.casegen.ai.proposal.domain.AiProposalType;
import com.casegen.ai.proposal.repository.AiProposalRepository;
import com.casegen.ai.proposal.service.AiProposalDecisionCommand;
import com.casegen.ai.proposal.service.AiProposalDecisionService;
import com.casegen.ai.proposal.service.AiProposalErrorCode;
import com.casegen.ai.proposal.service.AiProposalException;
import com.casegen.ai.session.domain.AiSession;
import com.casegen.ai.session.repository.AiSessionRepository;
import com.casegen.auth.domain.User;
import com.casegen.auth.repository.UserRepository;
import com.casegen.collaboration.RealtimeEventPublisher;
import com.casegen.modeling.command.CreateUmlClassifierCommand;
import com.casegen.modeling.domain.ClassifierKind;
import com.casegen.modeling.domain.Diagram;
import com.casegen.modeling.domain.DiagramType;
import com.casegen.modeling.domain.UmlModel;
import com.casegen.modeling.domain.Visibility;
import com.casegen.modeling.repository.DiagramRepository;
import com.casegen.modeling.dto.AddDiagramNodeRequest;
import com.casegen.modeling.service.DiagramService;
import com.casegen.modeling.repository.UmlModelRepository;
import com.casegen.modeling.service.UmlModelService;
import com.casegen.project.domain.Project;
import com.casegen.project.repository.ProjectRepository;
import com.casegen.project.service.ProjectAuthorizationService;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@Import(AiProposalDecisionConcurrencyIntegrationTest.RollbackConfiguration.class)
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = "jdbc:postgresql:.*")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AiProposalDecisionConcurrencyIntegrationTest {
    @MockitoBean
    ProjectAuthorizationService authorization;

    @MockitoBean
    RealtimeEventPublisher realtimeEventPublisher;

    private final AiProposalDecisionService decisions;
    private final UmlModelService umlModels;
    private final AiProposalRepository proposals;
    private final UserRepository users;
    private final ProjectRepository projects;
    private final AiSessionRepository sessions;
    private final AiMessageRepository messages;
    private final UmlModelRepository models;
    private final DiagramRepository diagrams;
    private final DiagramService diagramService;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final RollbackProbe rollbackProbe;
    private Fixture fixture;

    AiProposalDecisionConcurrencyIntegrationTest(AiProposalDecisionService decisions,
            UmlModelService umlModels, AiProposalRepository proposals, UserRepository users,
            ProjectRepository projects, AiSessionRepository sessions,
            AiMessageRepository messages, UmlModelRepository models, DiagramRepository diagrams,
            DiagramService diagramService, JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager, RollbackProbe rollbackProbe) {
        this.decisions = decisions;
        this.umlModels = umlModels;
        this.proposals = proposals;
        this.users = users;
        this.projects = projects;
        this.sessions = sessions;
        this.messages = messages;
        this.models = models;
        this.diagrams = diagrams;
        this.diagramService = diagramService;
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        this.rollbackProbe = rollbackProbe;
    }

    @AfterEach
    void cleanFixture() {
        if (fixture == null) return;
        UUID projectId = fixture.projectId();
        UUID modelId = fixture.modelId();
        jdbc.update("DELETE FROM ai_proposals WHERE project_id=?", projectId);
        jdbc.update("DELETE FROM diagram_edges WHERE diagram_id IN (SELECT id FROM diagrams WHERE model_id=?)", modelId);
        jdbc.update("DELETE FROM diagram_nodes WHERE diagram_id IN (SELECT id FROM diagrams WHERE model_id=?)", modelId);
        jdbc.update("DELETE FROM diagrams WHERE model_id=?", modelId);
        jdbc.update("DELETE FROM uml_association_ends WHERE association_id IN "
                + "(SELECT id FROM uml_elements WHERE model_id=?)", modelId);
        for (String table : new String[]{"uml_associations", "uml_generalizations", "uml_realizations",
                "uml_parameters", "uml_properties", "uml_operations", "uml_classifiers", "uml_packages"}) {
            jdbc.update("DELETE FROM " + table + " WHERE element_id IN "
                    + "(SELECT id FROM uml_elements WHERE model_id=?)", modelId);
        }
        jdbc.update("DELETE FROM uml_elements WHERE model_id=?", modelId);
        jdbc.update("DELETE FROM uml_models WHERE id=?", modelId);
        jdbc.update("DELETE FROM projects WHERE id=?", projectId);
        jdbc.update("DELETE FROM users WHERE id=?", fixture.userId());
        fixture = null;
    }

    @Test
    void applyPersistsClassifierAuditAndRevision() {
        fixture = createFixture("Apply");
        AiProposal applied = decisions.apply(command(fixture, "approved"));

        assertThat(applied.getStatus()).isEqualTo(AiProposalStatus.APPLIED);
        assertThat(applied.getDecidedBy().getId()).isEqualTo(fixture.userId());
        assertThat(applied.getDecidedAt()).isNotNull();
        assertThat(applied.getAppliedModelRevision()).isGreaterThan(fixture.baseRevision());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uml_elements WHERE id=? AND model_id=?",
                Integer.class, applied.getAppliedElementId(), fixture.modelId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uml_classifiers WHERE element_id=?",
                Integer.class, applied.getAppliedElementId())).isEqualTo(1);
    }

    @Test
    void classifierFromDiagramCreatesVisualNodeInItsOriginDiagram() {
        fixture = createFixture("DiagramApply", true);

        AiProposal applied = decisions.apply(command(fixture, null));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM diagram_nodes "
                        + "WHERE diagram_id=? AND uml_element_id=?", Integer.class,
                fixture.diagramId(), applied.getAppliedElementId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM diagram_nodes "
                        + "WHERE uml_element_id=? AND diagram_id<>?", Integer.class,
                applied.getAppliedElementId(), fixture.diagramId())).isZero();
    }

    @Test
    void associationProposalPersistsSemanticRelationshipAndDiagramEdge() {
        fixture = createRelationshipFixture("Association", AiProposalType.ADD_ASSOCIATION,
                true, true);

        AiProposal applied = decisions.apply(command(fixture, null));

        assertRelationshipApplied(applied, "uml_associations");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uml_association_ends "
                + "WHERE association_id=?", Integer.class, applied.getAppliedElementId())).isEqualTo(2);
    }

    @Test
    void generalizationProposalPersistsSemanticRelationshipAndDiagramEdge() {
        fixture = createRelationshipFixture("Generalization", AiProposalType.ADD_GENERALIZATION,
                true, true);

        AiProposal applied = decisions.apply(command(fixture, null));

        assertRelationshipApplied(applied, "uml_generalizations");
    }

    @Test
    void realizationProposalPersistsSemanticRelationshipAndDiagramEdge() {
        fixture = createRelationshipFixture("Realization", AiProposalType.ADD_REALIZATION,
                true, true);

        AiProposal applied = decisions.apply(command(fixture, null));

        assertRelationshipApplied(applied, "uml_realizations");
    }

    @Test
    void relationshipWithoutVisibleEndpointRemainsSemanticOnly() {
        fixture = createRelationshipFixture("PartialRelationship", AiProposalType.ADD_GENERALIZATION,
                true, false);

        AiProposal applied = decisions.apply(command(fixture, null));

        assertThat(applied.getStatus()).isEqualTo(AiProposalStatus.APPLIED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uml_generalizations WHERE element_id=?",
                Integer.class, applied.getAppliedElementId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM diagram_edges WHERE uml_element_id=?",
                Integer.class, applied.getAppliedElementId())).isZero();
    }

    @Test
    void relationshipWithoutDiagramRemainsSemanticOnly() {
        fixture = createRelationshipFixture("SemanticRelationship", AiProposalType.ADD_GENERALIZATION,
                false, false);

        AiProposal applied = decisions.apply(command(fixture, null));

        assertThat(applied.getStatus()).isEqualTo(AiProposalStatus.APPLIED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uml_generalizations WHERE element_id=?",
                Integer.class, applied.getAppliedElementId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM diagram_edges WHERE uml_element_id=?",
                Integer.class, applied.getAppliedElementId())).isZero();
    }

    @Test
    void outerFailureRollsBackSemanticRelationshipAndDiagramEdge() {
        fixture = createRelationshipFixture("RelationshipRollback",
                AiProposalType.ADD_GENERALIZATION, true, true);
        long modelRevision = modelRevision(fixture.modelId());
        long diagramRevision = jdbc.queryForObject(
                "SELECT revision FROM diagrams WHERE id=?", Long.class, fixture.diagramId());

        assertThatThrownBy(() -> rollbackProbe.applyThenFail(command(fixture, null)))
                .isInstanceOf(InjectedRollbackException.class);

        AiProposal persisted = proposals.findById(fixture.proposalId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(AiProposalStatus.PENDING);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uml_generalizations g "
                        + "JOIN uml_elements e ON e.id=g.element_id WHERE e.model_id=?",
                Integer.class, fixture.modelId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM diagram_edges WHERE diagram_id=?",
                Integer.class, fixture.diagramId())).isZero();
        assertThat(modelRevision(fixture.modelId())).isEqualTo(modelRevision);
        assertThat(jdbc.queryForObject("SELECT revision FROM diagrams WHERE id=?",
                Long.class, fixture.diagramId())).isEqualTo(diagramRevision);
    }

    @Test
    void rejectPersistsAuditWithoutMutatingModel() {
        fixture = createFixture("Reject");
        long revision = modelRevision(fixture.modelId());
        int elements = elementCount(fixture.modelId());

        AiProposal rejected = decisions.reject(command(fixture, "out of scope"));

        assertThat(rejected.getStatus()).isEqualTo(AiProposalStatus.REJECTED);
        assertThat(rejected.getDecisionNote()).isEqualTo("out of scope");
        assertThat(rejected.getAppliedElementId()).isNull();
        assertThat(rejected.getAppliedModelRevision()).isNull();
        assertThat(modelRevision(fixture.modelId())).isEqualTo(revision);
        assertThat(elementCount(fixture.modelId())).isEqualTo(elements);
    }

    @Test
    void staleCannotApplyButCanReject() {
        fixture = createFixture("Stale");
        umlModels.createClassifier(fixture.modelId(), classifier("Existing-" + UUID.randomUUID()));

        assertThatThrownBy(() -> decisions.apply(command(fixture, null)))
                .isInstanceOfSatisfying(AiProposalException.class,
                        error -> assertThat(error.getCode()).isEqualTo(AiProposalErrorCode.PROPOSAL_STALE));
        AiProposal rejected = decisions.reject(command(fixture, null));
        assertThat(rejected.getStatus()).isEqualTo(AiProposalStatus.REJECTED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uml_elements WHERE model_id=? AND name=?",
                Integer.class, fixture.modelId(), fixture.classifierName())).isZero();
    }

    @Test
    void proposalFromClosedSessionCanStillBeApplied() {
        fixture = createFixture("ClosedSession");
        transactions.executeWithoutResult(status -> {
            AiProposal proposal = proposals.findById(fixture.proposalId()).orElseThrow();
            proposal.getSession().close(Instant.now());
            sessions.saveAndFlush(proposal.getSession());
        });

        AiProposal applied = decisions.apply(command(fixture, null));

        assertThat(applied.getStatus()).isEqualTo(AiProposalStatus.APPLIED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uml_elements WHERE id=? AND model_id=?",
                Integer.class, applied.getAppliedElementId(), fixture.modelId())).isEqualTo(1);
    }

    @Test
    void concurrentApplyApplyHasExactlyOneWinnerAndOneElement() throws Exception {
        fixture = createFixture("ApplyApply");
        RaceResult race = race(() -> decisions.apply(command(fixture, null)),
                () -> decisions.apply(command(fixture, null)));
        assertOneWinner(race);
        assertThat(proposals.findById(fixture.proposalId()).orElseThrow().getStatus())
                .isEqualTo(AiProposalStatus.APPLIED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uml_elements WHERE model_id=? AND name=?",
                Integer.class, fixture.modelId(), fixture.classifierName())).isEqualTo(1);
    }

    @Test
    void concurrentRejectRejectHasExactlyOneWinnerAndNoMutation() throws Exception {
        fixture = createFixture("RejectReject");
        int elements = elementCount(fixture.modelId());
        RaceResult race = race(() -> decisions.reject(command(fixture, null)),
                () -> decisions.reject(command(fixture, null)));
        assertOneWinner(race);
        assertThat(proposals.findById(fixture.proposalId()).orElseThrow().getStatus())
                .isEqualTo(AiProposalStatus.REJECTED);
        assertThat(elementCount(fixture.modelId())).isEqualTo(elements);
    }

    @Test
    void concurrentApplyRejectAllowsOnlyOneTerminalDecision() throws Exception {
        fixture = createFixture("ApplyReject");
        RaceResult race = race(() -> decisions.apply(command(fixture, null)),
                () -> decisions.reject(command(fixture, null)));
        assertOneWinner(race);
        AiProposal finalProposal = proposals.findById(fixture.proposalId()).orElseThrow();
        int created = jdbc.queryForObject("SELECT count(*) FROM uml_elements WHERE model_id=? AND name=?",
                Integer.class, fixture.modelId(), fixture.classifierName());
        if (finalProposal.getStatus() == AiProposalStatus.APPLIED) assertThat(created).isEqualTo(1);
        else {
            assertThat(finalProposal.getStatus()).isEqualTo(AiProposalStatus.REJECTED);
            assertThat(created).isZero();
        }
    }

    @Test
    void outerFailureRollsBackPhysicalUmlAndProposalWrites() {
        fixture = createFixture("Rollback", true);
        long revision = modelRevision(fixture.modelId());

        assertThatThrownBy(() -> rollbackProbe.applyThenFail(command(fixture, null)))
                .isInstanceOf(InjectedRollbackException.class);

        AiProposal persisted = proposals.findById(fixture.proposalId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(AiProposalStatus.PENDING);
        assertThat(persisted.getDecidedBy()).isNull();
        assertThat(persisted.getDecidedAt()).isNull();
        assertThat(persisted.getAppliedElementId()).isNull();
        assertThat(persisted.getAppliedModelRevision()).isNull();
        assertThat(modelRevision(fixture.modelId())).isEqualTo(revision);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uml_elements WHERE model_id=? AND name=?",
                Integer.class, fixture.modelId(), fixture.classifierName())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM diagram_nodes WHERE diagram_id=?",
                Integer.class, fixture.diagramId())).isZero();
    }

    private Fixture createFixture(String label) {
        return createFixture(label, false);
    }

    private Fixture createFixture(String label, boolean withDiagram) {
        return transactions.execute(status -> {
            String suffix = label + "-" + UUID.randomUUID();
            User user = users.saveAndFlush(new User(suffix + "@test.local", "hash", "Decision tester"));
            Project project = projects.saveAndFlush(new Project("Decision " + suffix, null));
            AiSession session = sessions.saveAndFlush(new AiSession(project, user, Instant.now()));
            UmlModel model = models.saveAndFlush(new UmlModel(project, "Model " + suffix, null, user));
            Diagram diagram = withDiagram
                    ? diagrams.saveAndFlush(new Diagram(model, null, "Diagram " + suffix,
                            DiagramType.CLASS, user))
                    : null;
            AiMessage question = messages.saveAndFlush(AiMessage.userMessage(session, user,
                    "Suggest", model, diagram, UUID.randomUUID(), AiSkill.UML_REVIEWER, Instant.now()));
            AiMessage assistant = messages.saveAndFlush(AiMessage.assistantMessage(session,
                    "Add classifier", "GEMINI", model, diagram, question, Instant.now()));
            String classifierName = "Generated" + UUID.randomUUID().toString().replace("-", "");
            var payload = JsonMapper.builder().build().valueToTree(Map.of(
                    "kind", "CLASS", "name", classifierName, "visibility", "PUBLIC",
                    "abstractClassifier", false, "active", true));
            AiProposal proposal = proposals.saveAndFlush(AiProposal.pending(project, session,
                    assistant, user, model, diagram, AiProposalType.ADD_CLASSIFIER,
                    "Add " + classifierName, "Required", payload, (short) 1,
                    model.getRevision(), diagram == null ? null : diagram.getRevision(),
                    "GEMINI", UUID.randomUUID()));
            when(authorization.requireWrite(project.getId())).thenReturn(user);
            return new Fixture(user.getId(), project.getId(), model.getId(), proposal.getId(),
                    diagram == null ? null : diagram.getId(), model.getRevision(), classifierName);
        });
    }

    private Fixture createRelationshipFixture(String label, AiProposalType type,
            boolean withDiagram, boolean bothVisible) {
        return transactions.execute(status -> {
            String suffix = label + "-" + UUID.randomUUID();
            User user = users.saveAndFlush(new User(suffix + "@test.local", "hash", "Decision tester"));
            Project project = projects.saveAndFlush(new Project("Decision " + suffix, null));
            when(authorization.requireWrite(project.getId())).thenReturn(user);
            AiSession session = sessions.saveAndFlush(new AiSession(project, user, Instant.now()));
            UmlModel model = models.saveAndFlush(new UmlModel(project, "Model " + suffix, null, user));
            var first = umlModels.createClassifier(model.getId(), classifier("First-" + suffix));
            var secondKind = type == AiProposalType.ADD_REALIZATION
                    ? ClassifierKind.INTERFACE : ClassifierKind.CLASS;
            var second = umlModels.createClassifier(model.getId(), new CreateUmlClassifierCommand(
                    "Second-" + suffix, null, null, secondKind,
                    Visibility.PUBLIC, false, secondKind == ClassifierKind.CLASS));
            Diagram diagram = withDiagram
                    ? diagrams.saveAndFlush(new Diagram(model, null, "Diagram " + suffix,
                            DiagramType.CLASS, user))
                    : null;
            if (diagram != null) {
                diagramService.addNode(diagram.getId(), new AddDiagramNodeRequest(
                        first.id(), 50, 50, 310, 180, 0));
                if (bothVisible) {
                    diagramService.addNode(diagram.getId(), new AddDiagramNodeRequest(
                            second.id(), 430, 50, 310, 180, 1));
                }
            }
            AiMessage question = messages.saveAndFlush(AiMessage.userMessage(session, user,
                    "Suggest relationship", model, diagram, UUID.randomUUID(),
                    AiSkill.UML_REVIEWER, Instant.now()));
            AiMessage assistant = messages.saveAndFlush(AiMessage.assistantMessage(session,
                    "Add relationship", "GEMINI", model, diagram, question, Instant.now()));
            Object payload = switch (type) {
                case ADD_ASSOCIATION -> Map.of(
                        "name", "connects",
                        "firstEnd", Map.of("classifierId", first.id(), "lowerBound", 1,
                                "upperBound", 1, "navigable", false,
                                "aggregation", "NONE", "ownership", "ASSOCIATION"),
                        "secondEnd", Map.of("classifierId", second.id(), "lowerBound", 0,
                                "upperBound", 5, "navigable", true,
                                "aggregation", "NONE", "ownership", "ASSOCIATION"));
                case ADD_GENERALIZATION -> Map.of("specificClassifierId", first.id(),
                        "generalClassifierId", second.id(), "substitutable", true);
                case ADD_REALIZATION -> Map.of("clientClassifierId", first.id(),
                        "supplierClassifierId", second.id());
                default -> throw new IllegalArgumentException("Relationship type required");
            };
            var proposal = proposals.saveAndFlush(AiProposal.pending(project, session, assistant,
                    user, model, diagram, type, "Add relationship", "Required",
                    JsonMapper.builder().build().valueToTree(payload), (short) 1,
                    model.getRevision(), diagram == null ? null : diagram.getRevision(),
                    "GEMINI", UUID.randomUUID()));
            return new Fixture(user.getId(), project.getId(), model.getId(), proposal.getId(),
                    diagram == null ? null : diagram.getId(), model.getRevision(), null);
        });
    }

    private void assertRelationshipApplied(AiProposal applied, String semanticTable) {
        assertThat(applied.getStatus()).isEqualTo(AiProposalStatus.APPLIED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM " + semanticTable + " WHERE element_id=?",
                Integer.class, applied.getAppliedElementId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM diagram_edges "
                        + "WHERE diagram_id=? AND uml_element_id=?", Integer.class,
                fixture.diagramId(), applied.getAppliedElementId())).isEqualTo(1);
    }

    private AiProposalDecisionCommand command(Fixture value, String note) {
        return new AiProposalDecisionCommand(value.projectId(), value.proposalId(), value.userId(), note);
    }

    private CreateUmlClassifierCommand classifier(String name) {
        return new CreateUmlClassifierCommand(name, null, null, ClassifierKind.CLASS,
                Visibility.PUBLIC, false, true);
    }

    private RaceResult race(Task first, Task second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Throwable> one = executor.submit(() -> execute(ready, start, first));
            Future<Throwable> two = executor.submit(() -> execute(ready, start, second));
            ready.await();
            start.countDown();
            return new RaceResult(one.get(), two.get());
        }
    }

    private Throwable execute(CountDownLatch ready, CountDownLatch start, Task task) {
        ready.countDown();
        try {
            start.await();
            task.run();
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private void assertOneWinner(RaceResult race) {
        assertThat(java.util.stream.Stream.of(race.first(), race.second()).filter(java.util.Objects::isNull).count())
                .isEqualTo(1);
        Throwable loser = race.first() == null ? race.second() : race.first();
        assertThat(loser).isInstanceOfSatisfying(AiProposalException.class,
                error -> assertThat(error.getCode()).isEqualTo(AiProposalErrorCode.PROPOSAL_ALREADY_DECIDED));
    }

    private long modelRevision(UUID modelId) {
        return jdbc.queryForObject("SELECT revision FROM uml_models WHERE id=?", Long.class, modelId);
    }

    private int elementCount(UUID modelId) {
        return jdbc.queryForObject("SELECT count(*) FROM uml_elements WHERE model_id=?", Integer.class, modelId);
    }

    @FunctionalInterface
    private interface Task { void run(); }
    private record RaceResult(Throwable first, Throwable second) { }
    private record Fixture(UUID userId, UUID projectId, UUID modelId, UUID proposalId,
                           UUID diagramId, long baseRevision, String classifierName) { }

    static final class InjectedRollbackException extends RuntimeException { }

    static class RollbackProbe {
        private final AiProposalDecisionService decisions;
        RollbackProbe(AiProposalDecisionService decisions) { this.decisions = decisions; }

        @Transactional
        public void applyThenFail(AiProposalDecisionCommand command) {
            decisions.apply(command);
            throw new InjectedRollbackException();
        }
    }

    @TestConfiguration
    static class RollbackConfiguration {
        @Bean
        RollbackProbe rollbackProbe(AiProposalDecisionService decisions) {
            return new RollbackProbe(decisions);
        }
    }
}
