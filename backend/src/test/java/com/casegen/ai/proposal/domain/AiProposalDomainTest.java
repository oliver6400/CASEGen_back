package com.casegen.ai.proposal.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.casegen.ai.message.domain.AiMessage;
import com.casegen.ai.message.domain.AiSkill;
import com.casegen.ai.session.domain.AiSession;
import com.casegen.auth.domain.User;
import com.casegen.modeling.domain.Diagram;
import com.casegen.modeling.domain.DiagramType;
import com.casegen.modeling.domain.UmlModel;
import com.casegen.project.domain.Project;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class AiProposalDomainTest {
    private static final Instant NOW = Instant.parse("2026-10-27T12:00:00Z");
    private static final JsonNode PAYLOAD = JsonMapper.builder().build()
            .valueToTree(java.util.Map.of("name", "Order"));

    @Test
    void factoryCreatesOnlyPendingProposalFromAssistantUmlReviewer() {
        Fixture fixture = fixture(AiSkill.UML_REVIEWER, false);

        AiProposal proposal = pending(fixture, AiProposalType.ADD_CLASSIFIER, PAYLOAD, (short) 1,
                null, null, UUID.randomUUID());

        assertEquals(AiProposalStatus.PENDING, proposal.getStatus());
        assertEquals("GEMINI", proposal.getProvider());
        assertEquals(AiSkill.UML_REVIEWER, proposal.getSkill());
        assertEquals("Add classifier", proposal.getTitle());
        assertEquals("Because the model needs it", proposal.getRationale());
        assertNull(proposal.getDecidedBy());
        assertNull(proposal.getDecidedAt());
        assertNull(proposal.getDecisionNote());
        assertNull(proposal.getAppliedModelRevision());
        assertNull(proposal.getAppliedElementId());
    }

    @Test
    void applyDecisionRecordsNormalizedAuditAndCreatedElement() {
        Fixture fixture = fixture(AiSkill.UML_REVIEWER, false);
        AiProposal proposal = pending(fixture, AiProposalType.ADD_CLASSIFIER, PAYLOAD,
                (short) 1, null, null, UUID.randomUUID());
        UUID elementId = UUID.randomUUID();

        proposal.applyDecision(fixture.user(), NOW, "  Approved by team  ", 9L, elementId);

        assertEquals(AiProposalStatus.APPLIED, proposal.getStatus());
        assertSame(fixture.user(), proposal.getDecidedBy());
        assertEquals(NOW, proposal.getDecidedAt());
        assertEquals("Approved by team", proposal.getDecisionNote());
        assertEquals(9L, proposal.getAppliedModelRevision());
        assertEquals(elementId, proposal.getAppliedElementId());
    }

    @Test
    void rejectDecisionRecordsAuditWithoutAppliedResult() {
        Fixture fixture = fixture(AiSkill.UML_REVIEWER, false);
        AiProposal proposal = pending(fixture, AiProposalType.ADD_CLASSIFIER, PAYLOAD,
                (short) 1, null, null, UUID.randomUUID());

        proposal.rejectDecision(fixture.user(), NOW, "  Outside scope  ");

        assertEquals(AiProposalStatus.REJECTED, proposal.getStatus());
        assertSame(fixture.user(), proposal.getDecidedBy());
        assertEquals(NOW, proposal.getDecidedAt());
        assertEquals("Outside scope", proposal.getDecisionNote());
        assertNull(proposal.getAppliedModelRevision());
        assertNull(proposal.getAppliedElementId());
    }

    @Test
    void decisionNoteAcceptsNullBlankAndExactlyOneThousandCharacters() {
        Fixture fixture = fixture(AiSkill.UML_REVIEWER, false);
        AiProposal nullNote = pending(fixture, AiProposalType.ADD_CLASSIFIER, PAYLOAD,
                (short) 1, null, null, UUID.randomUUID());
        nullNote.rejectDecision(fixture.user(), NOW, null);
        assertNull(nullNote.getDecisionNote());

        AiProposal blankNote = pending(fixture, AiProposalType.ADD_CLASSIFIER, PAYLOAD,
                (short) 1, null, null, UUID.randomUUID());
        blankNote.rejectDecision(fixture.user(), NOW, "   ");
        assertNull(blankNote.getDecisionNote());

        String maximum = "x".repeat(1000);
        AiProposal maximumNote = pending(fixture, AiProposalType.ADD_CLASSIFIER, PAYLOAD,
                (short) 1, null, null, UUID.randomUUID());
        maximumNote.rejectDecision(fixture.user(), NOW, maximum);
        assertEquals(maximum, maximumNote.getDecisionNote());
    }

    @Test
    void decisionNoteOverOneThousandCharactersIsRejected() {
        Fixture fixture = fixture(AiSkill.UML_REVIEWER, false);
        AiProposal proposal = pending(fixture, AiProposalType.ADD_CLASSIFIER, PAYLOAD,
                (short) 1, null, null, UUID.randomUUID());
        assertThrows(IllegalArgumentException.class,
                () -> proposal.rejectDecision(fixture.user(), NOW, "x".repeat(1001)));
    }

    @Test
    void invalidDecisionArgumentsAreRejected() {
        Fixture fixture = fixture(AiSkill.UML_REVIEWER, false);
        assertThrows(NullPointerException.class, () -> pending(fixture,
                AiProposalType.ADD_CLASSIFIER, PAYLOAD, (short) 1, null, null, UUID.randomUUID())
                .applyDecision(null, NOW, null, 1L, UUID.randomUUID()));
        assertThrows(NullPointerException.class, () -> pending(fixture,
                AiProposalType.ADD_CLASSIFIER, PAYLOAD, (short) 1, null, null, UUID.randomUUID())
                .applyDecision(fixture.user(), null, null, 1L, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> pending(fixture,
                AiProposalType.ADD_CLASSIFIER, PAYLOAD, (short) 1, null, null, UUID.randomUUID())
                .applyDecision(fixture.user(), NOW, null, -1L, UUID.randomUUID()));
        assertThrows(NullPointerException.class, () -> pending(fixture,
                AiProposalType.ADD_CLASSIFIER, PAYLOAD, (short) 1, null, null, UUID.randomUUID())
                .applyDecision(fixture.user(), NOW, null, 1L, null));
        assertThrows(NullPointerException.class, () -> pending(fixture,
                AiProposalType.ADD_CLASSIFIER, PAYLOAD, (short) 1, null, null, UUID.randomUUID())
                .rejectDecision(null, NOW, null));
        assertThrows(NullPointerException.class, () -> pending(fixture,
                AiProposalType.ADD_CLASSIFIER, PAYLOAD, (short) 1, null, null, UUID.randomUUID())
                .rejectDecision(fixture.user(), null, null));
    }

    @Test
    void appliedAndRejectedProposalsAreTerminal() {
        Fixture fixture = fixture(AiSkill.UML_REVIEWER, false);
        AiProposal applied = pending(fixture, AiProposalType.ADD_CLASSIFIER, PAYLOAD,
                (short) 1, null, null, UUID.randomUUID());
        applied.applyDecision(fixture.user(), NOW, null, 1L, UUID.randomUUID());
        assertThrows(AiProposalDecisionException.class,
                () -> applied.applyDecision(fixture.user(), NOW, null, 2L, UUID.randomUUID()));
        assertThrows(AiProposalDecisionException.class,
                () -> applied.rejectDecision(fixture.user(), NOW, null));

        AiProposal rejected = pending(fixture, AiProposalType.ADD_CLASSIFIER, PAYLOAD,
                (short) 1, null, null, UUID.randomUUID());
        rejected.rejectDecision(fixture.user(), NOW, null);
        assertThrows(AiProposalDecisionException.class,
                () -> rejected.applyDecision(fixture.user(), NOW, null, 1L, UUID.randomUUID()));
        assertThrows(AiProposalDecisionException.class,
                () -> rejected.rejectDecision(fixture.user(), NOW, null));
    }

    @ParameterizedTest
    @EnumSource(value = AiProposalType.class, names = {"UPDATE_CLASSIFIER", "UPDATE_ATTRIBUTE"}, mode = EnumSource.Mode.EXCLUDE)
    void everyApprovedProposalTypeIsAllowed(AiProposalType type) {
        Fixture fixture = fixture(AiSkill.UML_REVIEWER, false);
        assertEquals(type, pending(fixture, type, PAYLOAD, (short) 1,
                null, null, UUID.randomUUID()).getProposalType());
    }

    @ParameterizedTest
    @EnumSource(value = AiProposalType.class, names = {"UPDATE_CLASSIFIER", "UPDATE_ATTRIBUTE"})
    void updateTypesRequireSchema2Batch(AiProposalType type) {
        Fixture fixture = fixture(AiSkill.UML_REVIEWER, false);
        assertThrows(IllegalArgumentException.class, () -> pending(fixture, type, PAYLOAD,
                (short) 1, null, null, UUID.randomUUID()));
    }

    @Test
    void userSourceIsRejected() {
        Fixture fixture = fixture(AiSkill.UML_REVIEWER, false);
        assertThrows(IllegalArgumentException.class, () -> AiProposal.pending(
                fixture.project(), fixture.session(), fixture.userMessage(), fixture.user(),
                null, null, AiProposalType.ADD_CLASSIFIER, "Title", "Reason", PAYLOAD,
                (short) 1, null, null, "GEMINI", UUID.randomUUID()));
    }

    @ParameterizedTest
    @EnumSource(value = AiSkill.class, names = {"GENERAL", "DATABASE_REVIEWER"})
    void nonUmlReviewerSourceIsRejected(AiSkill skill) {
        Fixture fixture = fixture(skill, false);
        assertThrows(IllegalArgumentException.class, () -> pending(fixture,
                AiProposalType.ADD_CLASSIFIER, PAYLOAD, (short) 1,
                null, null, UUID.randomUUID()));
    }

    @Test
    void blankTitleIsRejected() {
        Fixture fixture = fixture(AiSkill.UML_REVIEWER, false);
        assertThrows(IllegalArgumentException.class, () -> AiProposal.pending(
                fixture.project(), fixture.session(), fixture.assistant(), fixture.user(), null, null,
                AiProposalType.ADD_CLASSIFIER, "  ", "Reason", PAYLOAD, (short) 1,
                null, null, "GEMINI", UUID.randomUUID()));
    }

    @Test
    void blankRationaleIsRejected() {
        Fixture fixture = fixture(AiSkill.UML_REVIEWER, false);
        assertThrows(IllegalArgumentException.class, () -> AiProposal.pending(
                fixture.project(), fixture.session(), fixture.assistant(), fixture.user(), null, null,
                AiProposalType.ADD_CLASSIFIER, "Title", "  ", PAYLOAD, (short) 1,
                null, null, "GEMINI", UUID.randomUUID()));
    }

    @Test
    void nullAndNonObjectPayloadsAreRejected() {
        Fixture fixture = fixture(AiSkill.UML_REVIEWER, false);
        assertThrows(IllegalArgumentException.class, () -> pending(fixture,
                AiProposalType.ADD_CLASSIFIER, null, (short) 1, null, null, UUID.randomUUID()));
        JsonNode array = JsonMapper.builder().build().valueToTree(java.util.List.of("unsafe"));
        assertThrows(IllegalArgumentException.class, () -> pending(fixture,
                AiProposalType.ADD_CLASSIFIER, array, (short) 1, null, null, UUID.randomUUID()));
    }

    @Test
    void payloadSchemaVersionMustBePositive() {
        Fixture fixture = fixture(AiSkill.UML_REVIEWER, false);
        assertThrows(IllegalArgumentException.class, () -> pending(fixture,
                AiProposalType.ADD_CLASSIFIER, PAYLOAD, (short) 0, null, null, UUID.randomUUID()));
    }

    @Test
    void diagramRequiresModel() {
        Fixture fixture = fixture(AiSkill.UML_REVIEWER, true);
        assertThrows(IllegalArgumentException.class, () -> AiProposal.pending(
                fixture.project(), fixture.session(), fixture.assistant(), fixture.user(), null,
                fixture.diagram(), AiProposalType.ADD_CLASSIFIER, "Title", "Reason", PAYLOAD,
                (short) 1, null, 0L, "GEMINI", UUID.randomUUID()));
    }

    @Test
    void contextRequiresNonNegativeRevisions() {
        Fixture fixture = fixture(AiSkill.UML_REVIEWER, true);
        assertThrows(IllegalArgumentException.class, () -> pending(fixture,
                AiProposalType.ADD_CLASSIFIER, PAYLOAD, (short) 1, -1L, 0L, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> pending(fixture,
                AiProposalType.ADD_CLASSIFIER, PAYLOAD, (short) 1, 0L, -1L, UUID.randomUUID()));
    }

    @Test
    void contextRequiresMatchingRevisionPresence() {
        Fixture fixture = fixture(AiSkill.UML_REVIEWER, true);
        assertThrows(IllegalArgumentException.class, () -> pending(fixture,
                AiProposalType.ADD_CLASSIFIER, PAYLOAD, (short) 1, null, 0L, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> pending(fixture,
                AiProposalType.ADD_CLASSIFIER, PAYLOAD, (short) 1, 0L, null, UUID.randomUUID()));
    }

    @Test
    void clientProposalIdIsRequired() {
        Fixture fixture = fixture(AiSkill.UML_REVIEWER, false);
        assertThrows(NullPointerException.class, () -> pending(fixture,
                AiProposalType.ADD_CLASSIFIER, PAYLOAD, (short) 1, null, null, null));
    }

    @Test
    void statusCannotBeCreatedAppliedOrRejected() {
        assertFalse(Arrays.stream(AiProposal.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("apply")
                        || method.getName().equals("reject")
                        || method.getName().equals("setStatus")));
    }

    private AiProposal pending(Fixture fixture, AiProposalType type, JsonNode payload,
            short schemaVersion, Long modelRevision, Long diagramRevision, UUID clientId) {
        return AiProposal.pending(fixture.project(), fixture.session(), fixture.assistant(),
                fixture.user(), fixture.model(), fixture.diagram(), type, "  Add classifier  ",
                "  Because the model needs it  ", payload, schemaVersion, modelRevision,
                diagramRevision, "GEMINI", clientId);
    }

    private Fixture fixture(AiSkill skill, boolean withContext) {
        User user = new User("member@test.local", "hash", "Member");
        Project project = new Project("Project", null);
        AiSession session = new AiSession(project, user, NOW.minusSeconds(60));
        UmlModel model = withContext ? new UmlModel(project, "Model", null, user) : null;
        Diagram diagram = withContext ? new Diagram(model, null, "Class diagram", DiagramType.CLASS, user) : null;
        AiMessage userMessage = AiMessage.userMessage(session, user, "Question", model, diagram,
                UUID.randomUUID(), skill, NOW);
        AiMessage assistant = AiMessage.assistantMessage(session, "Answer", "OLLAMA", model,
                diagram, userMessage, NOW.plusSeconds(1));
        return new Fixture(user, project, session, model, diagram, userMessage, assistant);
    }

    private record Fixture(User user, Project project, AiSession session, UmlModel model,
                           Diagram diagram, AiMessage userMessage, AiMessage assistant) { }
}
