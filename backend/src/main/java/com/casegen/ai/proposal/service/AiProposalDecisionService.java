package com.casegen.ai.proposal.service;

import com.casegen.ai.proposal.context.AiProposalContextBuilder;
import com.casegen.ai.proposal.domain.AiProposal;
import com.casegen.ai.proposal.domain.AiProposalStatus;
import com.casegen.ai.proposal.payload.AiProposalPayloadParser;
import com.casegen.ai.proposal.repository.AiProposalRepository;
import com.casegen.ai.proposal.realtime.AiProposalEventPublisher;
import com.casegen.ai.proposal.validation.AiProposalPayloadValidator;
import com.casegen.ai.proposal.validation.AiProposalValidationException;
import com.casegen.auth.domain.User;
import com.casegen.modeling.domain.UmlModel;
import com.casegen.modeling.exception.SemanticException;
import com.casegen.modeling.repository.UmlModelRepository;
import com.casegen.modeling.service.DiagramService;
import com.casegen.project.service.ProjectAuthorizationService;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiProposalDecisionService {
    private final ProjectAuthorizationService authorization;
    private final AiProposalRepository proposals;
    private final UmlModelRepository models;
    private final AiProposalPayloadParser parser;
    private final AiProposalContextBuilder contexts;
    private final AiProposalPayloadValidator validator;
    private final AiProposalCommandMapper commandMapper;
    private final DiagramService diagrams;
    private final AiProposalEventPublisher events;
    private final Clock clock;
    private final AiProposalBatchDecisionService batchDecisions;

    @Autowired
    public AiProposalDecisionService(ProjectAuthorizationService authorization,
            AiProposalRepository proposals, UmlModelRepository models,
            AiProposalPayloadParser parser, AiProposalContextBuilder contexts,
            AiProposalPayloadValidator validator, AiProposalCommandMapper commandMapper,
            DiagramService diagrams, AiProposalEventPublisher events, Clock clock,
            AiProposalBatchDecisionService batchDecisions) {
        this.authorization = authorization;
        this.proposals = proposals;
        this.models = models;
        this.parser = parser;
        this.contexts = contexts;
        this.validator = validator;
        this.commandMapper = commandMapper;
        this.diagrams = diagrams;
        this.events = events;
        this.clock = clock;
        this.batchDecisions = batchDecisions;
    }

    AiProposalDecisionService(ProjectAuthorizationService authorization,
            AiProposalRepository proposals, UmlModelRepository models,
            AiProposalPayloadParser parser, AiProposalContextBuilder contexts,
            AiProposalPayloadValidator validator, AiProposalCommandMapper commandMapper,
            DiagramService diagrams, AiProposalEventPublisher events, Clock clock) {
        this(authorization, proposals, models, parser, contexts, validator, commandMapper,
                diagrams, events, clock, null);
    }

    @Transactional
    public AiProposal apply(AiProposalDecisionCommand command) {
        requireCommand(command);
        var batchId = proposals.findBatchId(command.projectId(), command.proposalId());
        if (batchId != null && batchId.isPresent()) {
            return batchDecisions.apply(command, batchId.get());
        }
        User actor = requireActor(command);
        AiProposal proposal = lockProposal(command);
        requirePending(proposal);
        requireSchema1(proposal);
        UmlModel referencedModel = proposal.getModel();
        if (referencedModel == null) {
            throw error(AiProposalErrorCode.PROPOSAL_NO_LONGER_VALID,
                    "Proposal has no UML model context");
        }
        UmlModel model = models.findByIdForUpdate(referencedModel.getId())
                .orElseThrow(() -> error(AiProposalErrorCode.PROPOSAL_NO_LONGER_VALID,
                        "Proposal UML model no longer exists"));
        if (!model.getProject().getId().equals(command.projectId())) {
            throw error(AiProposalErrorCode.PROPOSAL_NO_LONGER_VALID,
                    "Proposal UML model belongs to a different project");
        }
        if (!Objects.equals(model.getRevision(), proposal.getBaseModelRevision())) {
            throw error(AiProposalErrorCode.PROPOSAL_STALE,
                    "Proposal was generated for an older UML model revision");
        }

        AiProposalAppliedElement applied;
        try {
            var payload = parser.parse(proposal.getProposalType(), proposal.getPayload());
            var context = contexts.build(command.projectId(), model.getId(),
                    proposal.getDiagram() == null ? null : proposal.getDiagram().getId());
            var validated = validator.validate(proposal.getProposalType(), payload, context);
            applied = commandMapper.apply(model.getId(), proposal.getProposalType(), validated);
            if (proposal.getProposalType() == com.casegen.ai.proposal.domain.AiProposalType.ADD_CLASSIFIER
                    && proposal.getDiagram() != null) {
                diagrams.addClassifierNodeAutoPlaced(
                        proposal.getDiagram().getId(), applied.id());
            }
            if (proposal.getDiagram() != null && switch (proposal.getProposalType()) {
                case ADD_ASSOCIATION, ADD_GENERALIZATION, ADD_REALIZATION -> true;
                default -> false;
            }) {
                diagrams.addRelationshipEdgeIfPossible(
                        proposal.getDiagram().getId(), applied.id());
            }
        } catch (AiProposalValidationException | SemanticException exception) {
            throw new AiProposalException(AiProposalErrorCode.PROPOSAL_NO_LONGER_VALID,
                    "Proposal is no longer valid for the current UML model", exception);
        }

        Instant decidedAt = Instant.now(clock);
        proposal.applyDecision(actor, decidedAt, command.note(),
                applied.modelRevision(), applied.id());
        AiProposal saved = proposals.saveAndFlush(proposal);
        events.applied(command.projectId(), saved.getId(), decidedAt);
        return saved;
    }

    @Transactional
    public AiProposal reject(AiProposalDecisionCommand command) {
        requireCommand(command);
        var batchId = proposals.findBatchId(command.projectId(), command.proposalId());
        if (batchId != null && batchId.isPresent()) {
            return batchDecisions.reject(command, batchId.get());
        }
        User actor = requireActor(command);
        AiProposal proposal = lockProposal(command);
        requirePending(proposal);
        requireSchema1(proposal);
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

    private AiProposal lockProposal(AiProposalDecisionCommand command) {
        return proposals.findForDecision(command.projectId(), command.proposalId())
                .orElseThrow(() -> error(AiProposalErrorCode.PROPOSAL_NOT_FOUND,
                        "AI proposal does not exist"));
    }

    private void requirePending(AiProposal proposal) {
        if (proposal.getStatus() != AiProposalStatus.PENDING) {
            throw error(AiProposalErrorCode.PROPOSAL_ALREADY_DECIDED,
                    "AI proposal has already been decided");
        }
    }

    private void requireSchema1(AiProposal proposal) {
        // Persisted legacy proposals are schema 1. A zero can only occur in isolated mocks;
        // schema 2 is deliberately reserved for batched review proposals.
        if (proposal.getPayloadSchemaVersion() > 1) {
            throw error(AiProposalErrorCode.INVALID_REQUEST,
                    "Individual proposals require payload schema version 1");
        }
    }

    private void requireCommand(AiProposalDecisionCommand command) {
        if (command == null || command.projectId() == null || command.proposalId() == null) {
            throw error(AiProposalErrorCode.INVALID_REQUEST,
                    "Project and proposal are required");
        }
    }

    private AiProposalException error(AiProposalErrorCode code, String message) {
        return new AiProposalException(code, message);
    }
}
