package com.casegen.collaboration;

import java.time.Instant;
import java.util.UUID;

public record CollaborationActivityResponse(
        UUID id,
        UUID actorUserId,
        String actorDisplayName,
        RealtimeEventType eventType,
        UUID elementId,
        String description,
        Long modelRevision,
        Long diagramRevision,
        Instant createdAt) {
}
