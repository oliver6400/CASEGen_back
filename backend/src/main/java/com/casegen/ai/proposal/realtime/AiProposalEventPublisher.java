package com.casegen.ai.proposal.realtime;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class AiProposalEventPublisher {
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AiProposalEventPublisher(ApplicationEventPublisher events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    public AiProposalCreatedInternalEvent created(UUID projectId, UUID proposalId) {
        AiProposalCreatedInternalEvent event = new AiProposalCreatedInternalEvent(
                projectId, proposalId, clock.instant());
        events.publishEvent(event);
        return event;
    }

    public AiProposalAppliedInternalEvent applied(UUID projectId, UUID proposalId,
            Instant occurredAt) {
        var event = new AiProposalAppliedInternalEvent(projectId, proposalId, occurredAt);
        events.publishEvent(event);
        return event;
    }

    public AiProposalRejectedInternalEvent rejected(UUID projectId, UUID proposalId,
            Instant occurredAt) {
        var event = new AiProposalRejectedInternalEvent(projectId, proposalId, occurredAt);
        events.publishEvent(event);
        return event;
    }

    public AiProposalBatchAppliedInternalEvent batchApplied(UUID projectId, UUID batchId,
            int appliedCount, long finalModelRevision, Instant occurredAt) {
        var event = new AiProposalBatchAppliedInternalEvent(projectId, batchId, appliedCount,
                finalModelRevision, occurredAt);
        events.publishEvent(event);
        return event;
    }

    public AiProposalBatchCreatedInternalEvent batchCreated(UUID projectId, UUID batchId) {
        var event=new AiProposalBatchCreatedInternalEvent(projectId,batchId,clock.instant());
        events.publishEvent(event);
        return event;
    }
}
