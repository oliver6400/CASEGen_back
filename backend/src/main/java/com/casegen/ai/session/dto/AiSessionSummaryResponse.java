package com.casegen.ai.session.dto;

import com.casegen.ai.session.domain.AiSessionStatus;
import java.time.Instant;
import java.util.UUID;

public record AiSessionSummaryResponse(
        UUID id,
        UUID projectId,
        UUID hostUserId,
        String hostName,
        AiSessionStatus status,
        Instant startedAt,
        Instant endedAt,
        int participantCount) {
}
