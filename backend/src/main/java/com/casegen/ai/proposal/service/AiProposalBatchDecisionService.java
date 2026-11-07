package com.casegen.ai.proposal.service;

import com.casegen.ai.proposal.domain.*;
import com.casegen.ai.proposal.repository.*;
import com.casegen.ai.proposal.realtime.AiProposalEventPublisher;
import com.casegen.auth.domain.User;
import com.casegen.modeling.domain.UmlModel;
import com.casegen.modeling.repository.UmlModelRepository;
import com.casegen.project.service.ProjectAuthorizationService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** Applies persisted schema-2 proposals using the invariant lock order batch -> proposal -> model. */
@Service
public class AiProposalBatchDecisionService {
    private final ProjectAuthorizationService authorization;
    private final AiProposalBatchRepository batches;
    private final AiProposalRepository proposals;
    private final AiProposalDependencyRepository dependencyRepository;
    private final UmlModelRepository models;
    private final AiProposalBatchChildApplicator applicator;
    private final AiProposalEventPublisher events;
    private final Clock clock;

    @Autowired
    public AiProposalBatchDecisionService(ProjectAuthorizationService authorization,
            AiProposalBatchRepository batches, AiProposalRepository proposals,
            AiProposalDependencyRepository dependencyRepository, UmlModelRepository models,
            AiProposalBatchPayloadParser parser, AiProposalCommandMapper mapper,
            com.casegen.modeling.service.DiagramService diagrams,
            AiProposalEventPublisher events, Clock clock,
            AiProposalBatchChildApplicator applicator) {
        this.authorization = authorization;
        this.batches = batches;
        this.proposals = proposals;
        this.dependencyRepository = dependencyRepository;
        this.models = models;
        this.applicator = applicator;
        this.events = events;
        this.clock = clock;
    }

    AiProposalBatchDecisionService(ProjectAuthorizationService authorization,
            AiProposalBatchRepository batches, AiProposalRepository proposals,
            AiProposalDependencyRepository dependencyRepository, UmlModelRepository models,
            AiProposalBatchPayloadParser parser, AiProposalCommandMapper mapper,
            com.casegen.modeling.service.DiagramService diagrams,
            AiProposalEventPublisher events, Clock clock) {
        this(authorization, batches, proposals, dependencyRepository, models, parser, mapper,
                diagrams, events, clock,
                new AiProposalBatchChildApplicator(parser, mapper, diagrams, events));
    }

    @Transactional
    public AiProposal apply(AiProposalDecisionCommand command, UUID batchId) {
        User actor = requireActor(command);
        AiProposalBatch batch = lockBatch(command.projectId(), batchId);
        AiProposal proposal = lockChild(command, batchId);
        requirePending(proposal);
        requireSchema2(proposal);
        UmlModel model = models.findByIdForUpdate(batch.getModel().getId())
                .orElseThrow(() -> error(AiProposalErrorCode.PROPOSAL_NO_LONGER_VALID,
                        "Batch UML model no longer exists"));
        long cursor = batch.getModelRevisionCursor();
        if (model.getRevision() != cursor) {
            throw error(AiProposalErrorCode.BATCH_STALE,
                    "UML model changed outside this proposal batch");
        }
        List<AiProposalDependency> dependencies = dependencyRepository
                .findByProposal_Id(proposal.getId());
        if (dependencies.stream().map(AiProposalDependency::getDependsOn)
                .anyMatch(value -> value.getStatus() != AiProposalStatus.APPLIED)) {
            throw error(AiProposalErrorCode.PROPOSAL_DEPENDENCY_NOT_APPLIED,
                    "All proposal dependencies must be applied first");
        }

        Instant decidedAt = Instant.now(clock);
        applicator.apply(batch, proposal, dependencies, actor, decidedAt, command.note());
        AiProposal saved = proposals.saveAndFlush(proposal);
        batches.saveAndFlush(batch);
        return saved;
    }

    @Transactional
    public AiProposal reject(AiProposalDecisionCommand command, UUID batchId) {
        User actor = requireActor(command);
        lockBatch(command.projectId(), batchId);
        AiProposal proposal = lockChild(command, batchId);
        requirePending(proposal);
        requireSchema2(proposal);
        Instant decidedAt = Instant.now(clock);
        proposal.rejectDecision(actor, decidedAt, command.note());
        AiProposal saved = proposals.saveAndFlush(proposal);
        events.rejected(command.projectId(), saved.getId(), decidedAt);
        return saved;
    }

    private User requireActor(AiProposalDecisionCommand command) {
        User actor = authorization.requireWrite(command.projectId());
        if (command.actorUserId() == null || !command.actorUserId().equals(actor.getId())) {
            throw error(AiProposalErrorCode.INVALID_REQUEST,
                    "Decision actor must be the authenticated user");
        }
        return actor;
    }

    private AiProposalBatch lockBatch(UUID projectId, UUID batchId) {
        return batches.findForUpdate(projectId, batchId)
                .orElseThrow(() -> error(AiProposalErrorCode.PROPOSAL_NOT_FOUND,
                        "AI proposal batch does not exist"));
    }

    private AiProposal lockChild(AiProposalDecisionCommand command, UUID batchId) {
        AiProposal proposal = proposals.findForDecision(command.projectId(), command.proposalId())
                .orElseThrow(() -> error(AiProposalErrorCode.PROPOSAL_NOT_FOUND,
                        "AI proposal does not exist"));
        if (proposal.getBatch() == null || !batchId.equals(proposal.getBatch().getId())) {
            throw error(AiProposalErrorCode.PROPOSAL_NO_LONGER_VALID,
                    "Proposal does not belong to the locked batch");
        }
        return proposal;
    }

    private void requirePending(AiProposal proposal) {
        if (proposal.getStatus() != AiProposalStatus.PENDING) {
            throw error(AiProposalErrorCode.PROPOSAL_ALREADY_DECIDED,
                    "AI proposal has already been decided");
        }
    }

    private void requireSchema2(AiProposal proposal) {
        if (proposal.getPayloadSchemaVersion() != 2) {
            throw error(AiProposalErrorCode.INVALID_BATCH_PROPOSAL_PAYLOAD,
                    "Batch proposals require payload schema version 2");
        }
    }

    private AiProposalException error(AiProposalErrorCode code, String message) {
        return new AiProposalException(code, message);
    }
}
