package com.casegen.ai.proposal.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.casegen.ai.message.domain.*;
import com.casegen.ai.session.domain.AiSession;
import com.casegen.auth.domain.User;
import com.casegen.modeling.domain.*;
import com.casegen.project.domain.Project;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AiProposalBatchDomainTest {
    private static final Instant NOW = Instant.parse("2026-10-31T12:00:00Z");

    @Test void validBatchStartsCursorAtBaseAndAllowsNullableDiagram() {
        Fixture fixture = fixture(AiSkill.UML_REVIEWER, false);
        AiProposalBatch batch = batch(fixture, 7, null);
        assertEquals(7, batch.getBaseModelRevision());
        assertEquals(7, batch.getModelRevisionCursor());
        assertNull(batch.getDiagram());
        batch.advanceModelRevisionCursor(7, 8);
        assertEquals(8, batch.getModelRevisionCursor());
        assertThrows(IllegalStateException.class, () -> batch.advanceModelRevisionCursor(7, 9));
        assertThrows(IllegalArgumentException.class, () -> batch.advanceModelRevisionCursor(8, 7));
    }

    @Test void batchRejectsInvalidSourceContextAndRevisions() {
        Fixture userSource = fixture(AiSkill.UML_REVIEWER, false);
        assertThrows(IllegalArgumentException.class, () -> AiProposalBatch.create(userSource.project,
                userSource.session, userSource.question, userSource.user, userSource.model, null,
                "Review", "Summary", 0, null, "GEMINI", UUID.randomUUID()));
        Fixture wrongSkill = fixture(AiSkill.GENERAL, false);
        assertThrows(IllegalArgumentException.class, () -> batch(wrongSkill, 0, null));
        Fixture valid = fixture(AiSkill.UML_REVIEWER, true);
        assertThrows(IllegalArgumentException.class, () -> batch(valid, -1, 0L));
        assertThrows(IllegalArgumentException.class, () -> batch(valid, 0, -1L));
        assertThrows(NullPointerException.class, () -> AiProposalBatch.create(valid.project, valid.session,
                valid.assistant, valid.user, valid.model, valid.diagram, "Review", "Summary",
                0, 0L, "GEMINI", null));
    }

    @Test void batchRejectsModelOutsideProjectAndDiagramOutsideModel() {
        Fixture first = fixture(AiSkill.UML_REVIEWER, false);
        Project anotherProject = new Project("Other", null);
        UmlModel anotherModel = new UmlModel(anotherProject, "Other model", null, first.user);
        assertThrows(IllegalArgumentException.class, () -> AiProposalBatch.create(first.project,
                first.session, first.assistant, first.user, anotherModel, null, "Review", "Summary",
                0, null, "GEMINI", UUID.randomUUID()));

        Diagram foreignDiagram = new Diagram(anotherModel, null, "Foreign", DiagramType.CLASS, first.user);
        assertThrows(IllegalArgumentException.class, () -> AiProposalBatch.create(first.project,
                first.session, first.assistant, first.user, first.model, foreignDiagram, "Review", "Summary",
                0, 0L, "GEMINI", UUID.randomUUID()));
    }

    @Test void individualAndBatchedProposalShapesRemainDistinct() {
        Fixture fixture = fixture(AiSkill.UML_REVIEWER, false);
        AiProposal individual = individual(fixture);
        assertNull(individual.getBatch());
        assertNull(individual.getBatchOrder());
        assertNull(individual.getClientRef());

        AiProposalBatch batch = batch(fixture, 0, null);
        AiProposal child = child(batch, 1, "producto");
        assertSame(batch, child.getBatch());
        assertEquals(1, child.getBatchOrder());
        assertEquals("producto", child.getClientRef());
        assertThrows(IllegalArgumentException.class, () -> child(batch, 0, "bad"));
        assertThrows(IllegalArgumentException.class, () -> child(batch, 16, "bad"));
        assertThrows(IllegalArgumentException.class, () -> child(batch, 2, " "));
    }

    @Test void dependencyRequiresTwoDifferentChildrenFromSameBatch() {
        Fixture fixture = fixture(AiSkill.UML_REVIEWER, false);
        AiProposalBatch batch = batch(fixture, 0, null);
        AiProposal first = child(batch, 1, "first");
        AiProposal second = child(batch, 2, "second");
        assertNotNull(AiProposalDependency.create(second, first));
        assertThrows(IllegalArgumentException.class, () -> AiProposalDependency.create(first, first));
        assertThrows(IllegalArgumentException.class,
                () -> AiProposalDependency.create(first, individual(fixture)));
        AiProposalBatch other = batch(fixture, 0, null);
        assertThrows(IllegalArgumentException.class,
                () -> AiProposalDependency.create(first, child(other, 1, "other")));
    }

    private AiProposalBatch batch(Fixture f, long revision, Long diagramRevision) {
        return AiProposalBatch.create(f.project, f.session, f.assistant, f.user, f.model, f.diagram,
                " Review ", " Summary ", revision, diagramRevision, "GEMINI", UUID.randomUUID());
    }
    private AiProposal individual(Fixture f) {
        return AiProposal.pending(f.project, f.session, f.assistant, f.user, f.model, f.diagram,
                AiProposalType.ADD_CLASSIFIER, "Title", "Reason", JsonMapper.builder().build().valueToTree(Map.of("name", "X")),
                (short) 1, 0L, f.diagram == null ? null : 0L, "GEMINI", UUID.randomUUID());
    }
    private AiProposal child(AiProposalBatch batch, int order, String ref) {
        return AiProposal.pendingInBatch(batch, order, ref, AiProposalType.ADD_CLASSIFIER,
                "Title", "Reason", JsonMapper.builder().build().valueToTree(Map.of("name", ref)),
                (short) 1, UUID.randomUUID());
    }
    private Fixture fixture(AiSkill skill, boolean diagram) {
        User user = new User("member@test.local", "hash", "Member");
        Project project = new Project("Project", null);
        AiSession session = new AiSession(project, user, NOW);
        UmlModel model = new UmlModel(project, "Model", null, user);
        Diagram value = diagram ? new Diagram(model, null, "Diagram", DiagramType.CLASS, user) : null;
        AiMessage question = AiMessage.userMessage(session, user, "Question", model, value,
                UUID.randomUUID(), skill, NOW);
        AiMessage assistant = AiMessage.assistantMessage(session, "Answer", "GEMINI", model, value,
                question, NOW.plusSeconds(1));
        return new Fixture(user, project, session, model, value, question, assistant);
    }
    private record Fixture(User user, Project project, AiSession session, UmlModel model,
            Diagram diagram, AiMessage question, AiMessage assistant) { }
}
