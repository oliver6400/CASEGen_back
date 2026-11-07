package com.casegen.ai.proposal.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.casegen.ai.message.domain.AiMessage;
import com.casegen.ai.message.domain.AiSkill;
import com.casegen.ai.proposal.domain.AiProposal;
import com.casegen.ai.proposal.domain.AiProposalStatus;
import com.casegen.ai.proposal.domain.AiProposalType;
import com.casegen.ai.session.domain.AiSession;
import com.casegen.auth.domain.User;
import com.casegen.modeling.domain.Diagram;
import com.casegen.modeling.domain.UmlModel;
import com.casegen.project.domain.Project;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AiProposalMapperTest {
    private final AiProposalMapper mapper = new AiProposalMapper();

    @Test
    void computesStaleFlagsAndReturnsSafeIdentityFields() {
        AiProposal proposal = mock(AiProposal.class);
        Project project = mock(Project.class);
        AiSession session = mock(AiSession.class);
        AiMessage source = mock(AiMessage.class);
        User requester = mock(User.class);
        UmlModel model = mock(UmlModel.class);
        Diagram diagram = mock(Diagram.class);
        when(project.getId()).thenReturn(UUID.randomUUID());
        when(session.getId()).thenReturn(UUID.randomUUID());
        when(source.getId()).thenReturn(UUID.randomUUID());
        when(requester.getId()).thenReturn(UUID.randomUUID());
        when(requester.getDisplayName()).thenReturn("Visible name");
        when(model.getId()).thenReturn(UUID.randomUUID());
        when(model.getName()).thenReturn("Model");
        when(model.getRevision()).thenReturn(8L);
        when(diagram.getId()).thenReturn(UUID.randomUUID());
        when(diagram.getName()).thenReturn("Diagram");
        when(diagram.getRevision()).thenReturn(4L);
        when(proposal.getId()).thenReturn(UUID.randomUUID());
        when(proposal.getProject()).thenReturn(project);
        when(proposal.getSession()).thenReturn(session);
        when(proposal.getSourceMessage()).thenReturn(source);
        when(proposal.getRequestedBy()).thenReturn(requester);
        when(proposal.getModel()).thenReturn(model);
        when(proposal.getDiagram()).thenReturn(diagram);
        when(proposal.getStatus()).thenReturn(AiProposalStatus.PENDING);
        when(proposal.getProposalType()).thenReturn(AiProposalType.ADD_CLASSIFIER);
        when(proposal.getTitle()).thenReturn("Add class");
        when(proposal.getRationale()).thenReturn("Reason");
        when(proposal.getPayload()).thenReturn(JsonMapper.builder().build().valueToTree(Map.of("name", "Order")));
        when(proposal.getPayloadSchemaVersion()).thenReturn((short) 1);
        when(proposal.getBaseModelRevision()).thenReturn(7L);
        when(proposal.getBaseDiagramRevision()).thenReturn(4L);
        when(proposal.getProvider()).thenReturn("GEMINI");
        when(proposal.getSkill()).thenReturn(AiSkill.UML_REVIEWER);
        when(proposal.getAppliedModelRevision()).thenReturn(null);
        when(proposal.getCreatedAt()).thenReturn(Instant.EPOCH);
        when(proposal.getUpdatedAt()).thenReturn(Instant.EPOCH);

        AiProposalResponse response = mapper.toResponse(proposal);

        assertThat(response.modelStale()).isTrue();
        assertThat(response.diagramContextChanged()).isFalse();
        assertThat(response.requestedByName()).isEqualTo("Visible name");
        assertThat(response.provider()).isEqualTo("GEMINI");
        assertThat(response.skill()).isEqualTo(AiSkill.UML_REVIEWER);
        assertThat(response.decidedByUserId()).isNull();
        assertThat(response.decidedByName()).isNull();
        assertThat(response.decidedAt()).isNull();
        assertThat(response.decisionNote()).isNull();
        assertThat(response.appliedModelRevision()).isNull();
        assertThat(response.appliedElementId()).isNull();
    }

    @Test
    void mapsAppliedDecisionAuditWithoutExposingEmail() {
        AiProposal proposal = proposal();
        User decider = new User("private@test.local", "hash", "Decision maker");
        UUID elementId = UUID.randomUUID();
        Instant decidedAt = Instant.parse("2026-10-30T12:00:00Z");
        proposal.applyDecision(decider, decidedAt, " Approved ", 9L, elementId);

        AiProposalResponse response = mapper.toResponse(proposal);

        assertThat(response.status()).isEqualTo(AiProposalStatus.APPLIED);
        assertThat(response.decidedByName()).isEqualTo("Decision maker");
        assertThat(response.decidedAt()).isEqualTo(decidedAt);
        assertThat(response.decisionNote()).isEqualTo("Approved");
        assertThat(response.appliedModelRevision()).isEqualTo(9L);
        assertThat(response.appliedElementId()).isEqualTo(elementId);
        assertThat(response.toString()).doesNotContain("private@test.local");
    }

    @Test
    void mapsRejectedDecisionWithoutAppliedFields() {
        AiProposal proposal = proposal();
        User decider = new User("private@test.local", "hash", "Reviewer");
        Instant decidedAt = Instant.parse("2026-10-30T12:00:00Z");
        proposal.rejectDecision(decider, decidedAt, null);

        AiProposalResponse response = mapper.toResponse(proposal);

        assertThat(response.status()).isEqualTo(AiProposalStatus.REJECTED);
        assertThat(response.decidedByName()).isEqualTo("Reviewer");
        assertThat(response.decidedAt()).isEqualTo(decidedAt);
        assertThat(response.appliedModelRevision()).isNull();
        assertThat(response.appliedElementId()).isNull();
    }

    private AiProposal proposal() {
        User requester = new User("requester@test.local", "hash", "Requester");
        Project project = new Project("Project", null);
        Instant now = Instant.parse("2026-10-30T11:00:00Z");
        AiSession session = new AiSession(project, requester, now);
        AiMessage question = AiMessage.userMessage(session, requester, "Question", null, null,
                UUID.randomUUID(), AiSkill.UML_REVIEWER, now);
        AiMessage answer = AiMessage.assistantMessage(session, "Answer", "GEMINI", null, null,
                question, now.plusSeconds(1));
        return AiProposal.pending(project, session, answer, requester, null, null,
                AiProposalType.ADD_CLASSIFIER, "Add class", "Reason",
                JsonMapper.builder().build().valueToTree(Map.of("name", "Order")),
                (short) 1, null, null, "GEMINI", UUID.randomUUID());
    }
}
