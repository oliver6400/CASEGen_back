package com.casegen.ai.proposal.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.casegen.ai.proposal.domain.*;
import com.casegen.ai.proposal.repository.*;
import com.casegen.ai.proposal.realtime.AiProposalEventPublisher;
import com.casegen.ai.proposal.review.ReviewAddClassifierPayload;
import com.casegen.auth.domain.User;
import com.casegen.modeling.domain.UmlModel;
import com.casegen.modeling.repository.UmlModelRepository;
import com.casegen.modeling.service.DiagramService;
import com.casegen.project.service.ProjectAuthorizationService;
import com.casegen.project.domain.Project;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class AiProposalBatchDecisionServiceTest {
    private final UUID projectId = UUID.randomUUID();
    private final UUID proposalId = UUID.randomUUID();
    private final UUID batchId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID modelId = UUID.randomUUID();
    private final ProjectAuthorizationService authorization = mock(ProjectAuthorizationService.class);
    private final AiProposalBatchRepository batches = mock(AiProposalBatchRepository.class);
    private final AiProposalRepository proposals = mock(AiProposalRepository.class);
    private final AiProposalDependencyRepository dependencies = mock(AiProposalDependencyRepository.class);
    private final UmlModelRepository models = mock(UmlModelRepository.class);
    private final AiProposalBatchPayloadParser parser = mock(AiProposalBatchPayloadParser.class);
    private final AiProposalCommandMapper mapper = mock(AiProposalCommandMapper.class);
    private final DiagramService diagrams = mock(DiagramService.class);
    private final AiProposalEventPublisher events = mock(AiProposalEventPublisher.class);
    private AiProposalBatchDecisionService service;
    private AiProposalBatch batch;
    private AiProposal proposal;
    private User actor;

    @BeforeEach
    void setUp() {
        service = new AiProposalBatchDecisionService(authorization, batches, proposals,
                dependencies, models, parser, mapper, diagrams, events,
                Clock.fixed(Instant.parse("2026-10-31T12:00:00Z"), ZoneOffset.UTC));
        batch = mock(AiProposalBatch.class);
        proposal = mock(AiProposal.class);
        actor = mock(User.class);
        UmlModel referencedModel = mock(UmlModel.class);
        when(actor.getId()).thenReturn(actorId);
        when(authorization.requireWrite(projectId)).thenReturn(actor);
        when(batches.findForUpdate(projectId, batchId)).thenReturn(Optional.of(batch));
        when(proposals.findForDecision(projectId, proposalId)).thenReturn(Optional.of(proposal));
        when(proposal.getBatch()).thenReturn(batch);
        Project project = mock(Project.class);
        when(project.getId()).thenReturn(projectId);
        when(proposal.getProject()).thenReturn(project);
        when(proposal.getId()).thenReturn(proposalId);
        when(proposal.getPayload()).thenReturn(mock(JsonNode.class));
        when(batch.getId()).thenReturn(batchId);
        when(proposal.getStatus()).thenReturn(AiProposalStatus.PENDING);
        when(proposal.getPayloadSchemaVersion()).thenReturn((short) 2);
        when(proposal.getModel()).thenReturn(referencedModel);
        when(referencedModel.getId()).thenReturn(modelId);
        when(batch.getModel()).thenReturn(referencedModel);
    }

    @Test
    void dependencyMustBeAppliedBeforeChild() {
        AiProposalDependency dependency = mock(AiProposalDependency.class);
        AiProposal required = mock(AiProposal.class);
        UmlModel model = mock(UmlModel.class);
        when(batch.getModelRevisionCursor()).thenReturn(10L);
        when(models.findByIdForUpdate(modelId)).thenReturn(Optional.of(model));
        when(model.getRevision()).thenReturn(10L);
        when(dependency.getDependsOn()).thenReturn(required);
        when(required.getStatus()).thenReturn(AiProposalStatus.PENDING);
        when(dependencies.findByProposal_Id(proposalId)).thenReturn(List.of(dependency));

        assertThatThrownBy(() -> service.apply(command(), batchId))
                .isInstanceOfSatisfying(AiProposalException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getCode())
                                .isEqualTo(AiProposalErrorCode.PROPOSAL_DEPENDENCY_NOT_APPLIED));
        verifyNoInteractions(mapper);
    }

    @Test
    void successfulMutationAdvancesCursorAndRecordsDecision() {
        UmlModel model = mock(UmlModel.class);
        var payload = mock(ReviewAddClassifierPayload.class);
        UUID appliedId = UUID.randomUUID();
        when(dependencies.findByProposal_Id(proposalId)).thenReturn(List.of());
        when(batch.getModelRevisionCursor()).thenReturn(10L);
        when(models.findByIdForUpdate(modelId)).thenReturn(Optional.of(model));
        when(model.getRevision()).thenReturn(10L);
        when(proposal.getProposalType()).thenReturn(AiProposalType.ADD_CLASSIFIER);
        when(parser.parse(eq(AiProposalType.ADD_CLASSIFIER), nullable(JsonNode.class)))
                .thenReturn(payload);
        doReturn(new AiProposalAppliedElement(appliedId, 11L)).when(mapper)
                .applyBatch(nullable(UUID.class), nullable(AiProposal.class),
                        nullable(com.casegen.ai.proposal.review.AiReviewPayload.class),
                        nullable(List.class));
        when(proposals.saveAndFlush(proposal)).thenReturn(proposal);

        service.apply(command(), batchId);

        verify(batch).advanceModelRevisionCursor(10L, 11L);
        verify(proposal).applyDecision(actor, Instant.parse("2026-10-31T12:00:00Z"),
                "ok", 11L, appliedId);
        verify(events).applied(projectId, proposalId, Instant.parse("2026-10-31T12:00:00Z"));
    }

    @Test
    void externalModelMutationProducesBatchStaleWithoutChangingCursor() {
        UmlModel model = mock(UmlModel.class);
        when(dependencies.findByProposal_Id(proposalId)).thenReturn(List.of());
        when(batch.getModelRevisionCursor()).thenReturn(10L);
        when(models.findByIdForUpdate(modelId)).thenReturn(Optional.of(model));
        when(model.getRevision()).thenReturn(11L);

        assertThatThrownBy(() -> service.apply(command(), batchId))
                .isInstanceOfSatisfying(AiProposalException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getCode())
                                .isEqualTo(AiProposalErrorCode.BATCH_STALE));
        verify(batch, never()).advanceModelRevisionCursor(anyLong(), anyLong());
    }

    @Test
    void rejectionDoesNotLockModelOrAdvanceCursorEvenWhenModelCouldBeStale() {
        when(proposals.saveAndFlush(proposal)).thenReturn(proposal);

        service.reject(command(), batchId);

        verify(proposal).rejectDecision(actor, Instant.parse("2026-10-31T12:00:00Z"), "ok");
        verifyNoInteractions(models);
        verify(batch, never()).advanceModelRevisionCursor(anyLong(), anyLong());
        verify(events).rejected(projectId, proposalId, Instant.parse("2026-10-31T12:00:00Z"));
    }

    private AiProposalDecisionCommand command() {
        return new AiProposalDecisionCommand(projectId, proposalId, actorId, "ok");
    }
}
