package com.casegen.collaboration;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PresenceSnapshot(UUID diagramId, List<PresenceParticipant> participants, Instant occurredAt) {
}
