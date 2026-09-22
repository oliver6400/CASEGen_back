package com.casegen.collaboration;

import java.time.Instant;
import java.util.UUID;

public record PresenceParticipant(
        UUID userId,
        String displayName,
        UUID diagramId,
        UUID selectedElementId,
        PresenceActivity activity,
        Instant lastSeen) {
}
