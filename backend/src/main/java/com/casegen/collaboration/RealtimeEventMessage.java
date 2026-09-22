package com.casegen.collaboration;

import java.time.Instant;
import java.util.UUID;

public record RealtimeEventMessage(
        UUID eventId,
        RealtimeEventType eventType,
        UUID projectId,
        UUID modelId,
        UUID diagramId,
        UUID entityId,
        Long modelRevision,
        Long diagramRevision,
        UUID actorUserId,
        String actorDisplayName,
        String description,
        Instant occurredAt) {

    public static RealtimeEventMessage from(RealtimeEvent event, CollaborationActivityResponse activity) {
        return new RealtimeEventMessage(event.eventId(), event.eventType(), event.projectId(), event.modelId(),
                event.diagramId(), event.entityId(), event.modelRevision(), event.diagramRevision(),
                event.actorUserId(), activity == null ? null : activity.actorDisplayName(),
                activity == null ? null : activity.description(), event.occurredAt());
    }
}
