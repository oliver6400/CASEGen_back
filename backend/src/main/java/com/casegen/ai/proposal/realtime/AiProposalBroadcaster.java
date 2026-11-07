package com.casegen.ai.proposal.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AiProposalBroadcaster {
    private static final Logger LOGGER = LoggerFactory.getLogger(AiProposalBroadcaster.class);
    private final AiProposalQueryService query;
    private final SimpMessagingTemplate messaging;

    public AiProposalBroadcaster(AiProposalQueryService query, SimpMessagingTemplate messaging) {
        this.query = query;
        this.messaging = messaging;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void broadcast(AiProposalCreatedInternalEvent internal) {
        broadcast(internal.projectId(), internal.proposalId(), internal.occurredAt(),
                AiProposalEvent.PROPOSAL_CREATED);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void broadcast(AiProposalAppliedInternalEvent internal) {
        broadcast(internal.projectId(), internal.proposalId(), internal.occurredAt(),
                AiProposalEvent.PROPOSAL_APPLIED);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void broadcast(AiProposalRejectedInternalEvent internal) {
        broadcast(internal.projectId(), internal.proposalId(), internal.occurredAt(),
                AiProposalEvent.PROPOSAL_REJECTED);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void broadcast(AiProposalBatchCreatedInternalEvent internal) {
        var event=new AiProposalBatchCreatedEvent(java.util.UUID.randomUUID(),
                AiProposalBatchCreatedEvent.EVENT_TYPE,internal.projectId(),internal.batchId(),internal.occurredAt());
        messaging.convertAndSend("/topic/projects/"+internal.projectId()+"/ai",event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void broadcast(AiProposalBatchAppliedInternalEvent internal) {
        var event = new AiProposalBatchAppliedEvent(java.util.UUID.randomUUID(),
                AiProposalBatchAppliedEvent.EVENT_TYPE, internal.projectId(), internal.batchId(),
                internal.appliedCount(), internal.finalModelRevision(), internal.occurredAt());
        messaging.convertAndSend("/topic/projects/" + internal.projectId() + "/ai", event);
    }

    private void broadcast(java.util.UUID projectId, java.util.UUID proposalId,
            java.time.Instant occurredAt, String eventType) {
        try {
            var response = query.loadResponse(projectId, proposalId);
            var event = new AiProposalEvent(java.util.UUID.randomUUID(),
                    eventType, projectId, response, occurredAt);
            messaging.convertAndSend("/topic/projects/" + projectId + "/ai", event);
        } catch (RuntimeException failure) {
            LOGGER.error("Failed to broadcast {} for projectId={} proposalId={} failureType={}",
                    eventType, projectId, proposalId, failure.getClass().getSimpleName());
        }
    }
}
