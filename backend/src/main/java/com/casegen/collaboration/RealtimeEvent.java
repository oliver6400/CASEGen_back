package com.casegen.collaboration;

import java.time.Instant;
import java.util.UUID;

public record RealtimeEvent(
        UUID eventId,
        RealtimeEventType eventType,
        UUID projectId,
        UUID modelId,
        UUID diagramId,
        UUID entityId,
        Long modelRevision,
        Long diagramRevision,
        UUID actorUserId,
        Instant occurredAt) {
}
