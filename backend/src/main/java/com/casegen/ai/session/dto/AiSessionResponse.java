package com.casegen.ai.session.dto;

import com.casegen.ai.session.domain.AiSessionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AiSessionResponse(
        UUID id,
        UUID projectId,
        UUID hostUserId,
        String hostName,
        AiSessionStatus status,
        Instant startedAt,
        Instant endedAt,
        Instant createdAt,
        Instant updatedAt,
        List<ParticipantResponse> participants) {
}
