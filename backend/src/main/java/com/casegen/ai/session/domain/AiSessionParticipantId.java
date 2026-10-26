package com.casegen.ai.session.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class AiSessionParticipantId implements Serializable {

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    protected AiSessionParticipantId() {
    }

    public AiSessionParticipantId(UUID sessionId, UUID userId) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId is required");
        this.userId = Objects.requireNonNull(userId, "userId is required");
    }

    public UUID getSessionId() { return sessionId; }
    public UUID getUserId() { return userId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AiSessionParticipantId that)) return false;
        return sessionId.equals(that.sessionId) && userId.equals(that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, userId);
    }
}
