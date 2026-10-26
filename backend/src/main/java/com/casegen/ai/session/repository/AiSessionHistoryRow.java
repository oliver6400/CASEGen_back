package com.casegen.ai.session.repository;

import com.casegen.ai.session.domain.AiSessionStatus;
import java.time.Instant;
import java.util.UUID;

public interface AiSessionHistoryRow {
    UUID getId();
    UUID getProjectId();
    UUID getHostUserId();
    String getHostName();
    AiSessionStatus getStatus();
    Instant getStartedAt();
    Instant getEndedAt();
    long getParticipantCount();
}
