package com.casegen.ai.session.dto;

import java.time.Instant;
import java.util.UUID;

public record ParticipantResponse(
        UUID userId,
        String displayName,
        Instant joinedAt,
        Instant lastSeenAt) {
}
