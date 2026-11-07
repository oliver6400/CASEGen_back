package com.casegen.ai.proposal.service;

import com.casegen.ai.proposal.domain.*;
import com.casegen.ai.proposal.realtime.AiProposalEventPublisher;
import com.casegen.auth.domain.User;
import com.casegen.modeling.exception.SemanticException;
import com.casegen.modeling.service.DiagramService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AiProposalBatchChildApplicator {
    private final AiProposalBatchPayloadParser parser;
    private final AiProposalCommandMapper mapper;
    private final DiagramService diagrams;
    private final AiProposalEventPublisher events;

    public AiProposalBatchChildApplicator(AiProposalBatchPayloadParser parser,
            AiProposalCommandMapper mapper, DiagramService diagrams,
            AiProposalEventPublisher events) {
        this.parser = parser;
        this.mapper = mapper;
        this.diagrams = diagrams;
        this.events = events;
    }

    public AiProposalAppliedElement apply(AiProposalBatch batch, AiProposal proposal,
            List<AiProposalDependency> dependencies, User actor, Instant decidedAt, String note) {
        if (proposal.getPayloadSchemaVersion() != 2) {
            throw error(AiProposalErrorCode.INVALID_BATCH_PROPOSAL_PAYLOAD,
                    "Batch proposals require payload schema version 2");
        }
        long cursor = batch.getModelRevisionCursor();
        AiProposalAppliedElement applied;
        try {
            var payload = parser.parse(proposal.getProposalType(), proposal.getPayload());
            applied = mapper.applyBatch(batch.getModel().getId(), proposal, payload, dependencies);
            if (proposal.getProposalType() == AiProposalType.ADD_CLASSIFIER
                    && proposal.getDiagram() != null) {
                diagrams.addClassifierNodeAutoPlaced(proposal.getDiagram().getId(), applied.id());
            }
            if (proposal.getDiagram() != null && switch (proposal.getProposalType()) {
                case ADD_ASSOCIATION, ADD_GENERALIZATION, ADD_REALIZATION -> true;
                default -> false;
            }) {
                diagrams.addRelationshipEdgeIfPossible(proposal.getDiagram().getId(), applied.id());
            }
        } catch (AiProposalException exception) {
            throw exception;
        } catch (SemanticException | IllegalArgumentException exception) {
            throw new AiProposalException(AiProposalErrorCode.PROPOSAL_NO_LONGER_VALID,
                    "Batch proposal is no longer valid for the current UML model", exception);
        }
        proposal.applyDecision(actor, decidedAt, note, applied.modelRevision(), applied.id());
        batch.advanceModelRevisionCursor(cursor, applied.modelRevision());
        events.applied(proposal.getProject().getId(), proposal.getId(), decidedAt);
        return applied;
    }

    private AiProposalException error(AiProposalErrorCode code, String message) {
        return new AiProposalException(code, message);
    }
}
