package com.casegen.ai.proposal.review;

import com.casegen.ai.message.domain.AiMessage;
import com.casegen.ai.message.repository.AiMessageRepository;
import com.casegen.ai.proposal.domain.*;
import com.casegen.ai.proposal.repository.*;
import com.casegen.ai.proposal.service.*;
import com.casegen.ai.proposal.realtime.AiProposalEventPublisher;
import com.casegen.auth.domain.User;
import com.casegen.auth.repository.UserRepository;
import com.casegen.modeling.domain.*;
import com.casegen.modeling.repository.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiProposalBatchPersistenceService {
    private final AiProposalBatchRepository batches;
    private final AiProposalRepository proposals;
    private final AiProposalDependencyRepository dependencies;
    private final AiMessageRepository messages;
    private final UserRepository users;
    private final UmlModelRepository models;
    private final DiagramRepository diagrams;
    private final AiProposalEventPublisher events;

    public AiProposalBatchPersistenceService(AiProposalBatchRepository batches,
            AiProposalRepository proposals, AiProposalDependencyRepository dependencies,
            AiMessageRepository messages, UserRepository users, UmlModelRepository models,
            DiagramRepository diagrams, AiProposalEventPublisher events) {
        this.batches = batches; this.proposals = proposals; this.dependencies = dependencies;
        this.messages = messages; this.users = users; this.models = models; this.diagrams = diagrams;
        this.events=events;
    }

    @Transactional
    public CreatedAiProposalBatch persist(CreateAiProposalBatchCommand command,
            ValidatedAiReview review) {
        AiProposalBatch replay = batches
                .findByProject_IdAndClientBatchId(command.projectId(), command.clientBatchId())
                .orElse(null);
        if (replay != null) return compatibleReplay(command, replay);

        AiMessage source = messages.findDetailedById(command.sourceMessageId())
                .orElseThrow(() -> invalid("source message no longer exists"));
        User actor = users.findById(command.actorUserId())
                .orElseThrow(() -> invalid("requester no longer exists"));
        UmlModel model = models.findByIdForUpdate(review.modelId())
                .orElseThrow(() -> invalid("model no longer exists"));
        Diagram diagram = review.diagramId() == null ? null : diagrams.findById(review.diagramId())
                .orElseThrow(() -> invalid("diagram no longer exists"));
        validateContext(command, review, source, actor, model, diagram);

        AiProposalBatch batch = batches.saveAndFlush(AiProposalBatch.create(
                source.getSession().getProject(), source.getSession(), source, actor, model, diagram,
                review.title(), review.summary(), review.baseModelRevision(),
                review.baseDiagramRevision(), review.provider(), command.clientBatchId()));

        Map<String, AiProposal> byRef = new LinkedHashMap<>();
        for (ValidatedAiReview.Item item : review.items()) {
            AiProposal proposal = AiProposal.pendingInBatch(batch, item.batchOrder(), item.clientRef(),
                    item.proposalType(), item.title(), item.rationale(), item.canonicalPayload(),
                    (short) 2, UUID.randomUUID());
            byRef.put(item.clientRef(), proposals.save(proposal));
        }
        proposals.flush();
        List<AiProposalDependency> rows = new ArrayList<>();
        for (ValidatedAiReview.Item item : review.items()) {
            for (String dependency : item.dependencies()) {
                rows.add(AiProposalDependency.create(byRef.get(item.clientRef()), byRef.get(dependency)));
            }
        }
        dependencies.saveAll(rows);
        dependencies.flush();
        events.batchCreated(command.projectId(),batch.getId());
        return new CreatedAiProposalBatch(batch, false);
    }

    private void validateContext(CreateAiProposalBatchCommand command, ValidatedAiReview review,
            AiMessage source, User actor, UmlModel model, Diagram diagram) {
        boolean valid = source.getSession() != null
                && source.getSession().getId().equals(command.sessionId())
                && source.getSession().getProject().getId().equals(command.projectId())
                && source.getModel() != null && source.getModel().getId().equals(model.getId())
                && actor.getId().equals(command.actorUserId())
                && model.getProject().getId().equals(command.projectId())
                && model.getRevision() == review.baseModelRevision()
                && Objects.equals(source.getDiagram() == null ? null : source.getDiagram().getId(),
                        review.diagramId())
                && Objects.equals(diagram == null ? null : diagram.getRevision(),
                        review.baseDiagramRevision());
        if (!valid) throw invalid("review context or revision changed before persistence");
    }

    private CreatedAiProposalBatch compatibleReplay(CreateAiProposalBatchCommand command,
            AiProposalBatch batch) {
        if (!batch.getSession().getId().equals(command.sessionId())
                || !batch.getSourceMessage().getId().equals(command.sourceMessageId())
                || !batch.getRequestedBy().getId().equals(command.actorUserId())) {
            throw new AiProposalException(AiProposalErrorCode.CLIENT_BATCH_ID_REUSED,
                    "clientBatchId is already associated with another request");
        }
        return new CreatedAiProposalBatch(batch, true);
    }

    private AiProposalException invalid(String message) {
        return new AiProposalException(AiProposalErrorCode.CONTEXT_REVISION_CHANGED, message);
    }
}
