package com.casegen.ai.proposal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.casegen.ai.proposal.domain.*;
import com.casegen.ai.proposal.repository.*;
import com.casegen.ai.proposal.realtime.AiProposalEventPublisher;
import com.casegen.auth.domain.User;
import com.casegen.modeling.domain.UmlModel;
import com.casegen.modeling.repository.UmlModelRepository;
import com.casegen.project.service.ProjectAuthorizationService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiProposalBatchApplyAllServiceTest {
    private final ProjectAuthorizationService authorization = mock(ProjectAuthorizationService.class);
    private final AiProposalBatchRepository batches = mock(AiProposalBatchRepository.class);
    private final AiProposalRepository proposals = mock(AiProposalRepository.class);
    private final AiProposalDependencyRepository dependencies = mock(AiProposalDependencyRepository.class);
    private final UmlModelRepository models = mock(UmlModelRepository.class);
    private final AiProposalBatchChildApplicator applicator = mock(AiProposalBatchChildApplicator.class);
    private final AiProposalEventPublisher events = mock(AiProposalEventPublisher.class);
    private final UUID projectId = UUID.randomUUID(), batchId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID(), modelId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-31T12:00:00Z");
    private AiProposalBatchApplyAllService service;
    private AiProposalBatch batch;
    private User actor;
    private UmlModel model;

    @BeforeEach
    void setUp() {
        service = new AiProposalBatchApplyAllService(authorization, batches, proposals,
                dependencies, models, applicator, events, Clock.fixed(now, ZoneOffset.UTC));
        batch = mock(AiProposalBatch.class);
        actor = mock(User.class);
        model = mock(UmlModel.class);
        when(actor.getId()).thenReturn(actorId);
        when(authorization.requireWrite(projectId)).thenReturn(actor);
        when(batches.findForUpdate(projectId, batchId)).thenReturn(Optional.of(batch));
        when(batch.getId()).thenReturn(batchId);
        when(batch.getModel()).thenReturn(model);
        when(model.getId()).thenReturn(modelId);
        when(models.findByIdForUpdate(modelId)).thenReturn(Optional.of(model));
        when(batch.getModelRevisionCursor()).thenReturn(10L);
        when(model.getRevision()).thenReturn(10L);
    }

    @Test
    void appliesPendingChildrenInDependencyOrderAndMarksBatch() {
        AiProposal dependency = child(2);
        AiProposal dependent = child(1);
        AiProposalDependency edge = mock(AiProposalDependency.class);
        when(edge.getProposal()).thenReturn(dependent);
        when(edge.getDependsOn()).thenReturn(dependency);
        when(proposals.findBatchChildrenForUpdate(batchId)).thenReturn(List.of(dependent, dependency));
        when(dependencies.findByBatchId(batchId)).thenReturn(List.of(edge));

        var result = service.apply(command());

        var order = inOrder(applicator);
        order.verify(applicator).apply(eq(batch), eq(dependency), anyList(), eq(actor), eq(now), eq("ok"));
        order.verify(applicator).apply(eq(batch), eq(dependent), anyList(), eq(actor), eq(now), eq("ok"));
        verify(batch).recordApplyAll(actor, now);
        verify(events).batchApplied(projectId, batchId, 2, 10L, now);
        assertThat(result.appliedCount()).isEqualTo(2);
    }

    @Test
    void rejectedDependencyBlocksWholeExecution() {
        AiProposal pending = child(2);
        AiProposal rejected = mock(AiProposal.class);
        when(rejected.getStatus()).thenReturn(AiProposalStatus.REJECTED);
        AiProposalDependency edge = mock(AiProposalDependency.class);
        when(edge.getProposal()).thenReturn(pending);
        when(edge.getDependsOn()).thenReturn(rejected);
        when(proposals.findBatchChildrenForUpdate(batchId)).thenReturn(List.of(rejected, pending));
        when(dependencies.findByBatchId(batchId)).thenReturn(List.of(edge));

        assertCode(AiProposalErrorCode.BATCH_HAS_UNSATISFIED_DEPENDENCY);
        verifyNoInteractions(applicator);
        verify(batch, never()).recordApplyAll(any(), any());
    }

    @Test
    void staleBatchFailsBeforeAnyChildMutation() {
        AiProposal pending = child(1);
        when(proposals.findBatchChildrenForUpdate(batchId)).thenReturn(List.of(pending));
        when(model.getRevision()).thenReturn(11L);

        assertCode(AiProposalErrorCode.BATCH_STALE);
        verifyNoInteractions(applicator);
    }

    @Test
    void batchWithoutPendingChildrenIsAlreadyDecided() {
        AiProposal applied = mock(AiProposal.class);
        when(applied.getStatus()).thenReturn(AiProposalStatus.APPLIED);
        when(proposals.findBatchChildrenForUpdate(batchId)).thenReturn(List.of(applied));

        assertCode(AiProposalErrorCode.BATCH_ALREADY_DECIDED);
        verifyNoInteractions(models, applicator);
    }

    private AiProposal child(int order) {
        AiProposal proposal = mock(AiProposal.class);
        when(proposal.getId()).thenReturn(UUID.randomUUID());
        when(proposal.getStatus()).thenReturn(AiProposalStatus.PENDING);
        when(proposal.getBatchOrder()).thenReturn(order);
        return proposal;
    }

    private AiProposalBatchApplyAllCommand command() {
        return new AiProposalBatchApplyAllCommand(projectId, batchId, actorId, "ok");
    }

    private void assertCode(AiProposalErrorCode code) {
        assertThatThrownBy(() -> service.apply(command()))
                .isInstanceOfSatisfying(AiProposalException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(code));
    }
}
