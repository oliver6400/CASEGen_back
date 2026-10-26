package com.casegen.ai.session.domain;

import com.casegen.auth.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "ai_session_participants")
public class AiSessionParticipant {

    @EmbeddedId
    private AiSessionParticipantId id;

    @NotNull
    @MapsId("sessionId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ai_session_participants_session"))
    private AiSession session;

    @NotNull
    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ai_session_participants_user"))
    private User user;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    protected AiSessionParticipant() {
    }

    public AiSessionParticipant(AiSession session, User user, Instant joinedAt) {
        this.session = Objects.requireNonNull(session, "session is required");
        this.user = Objects.requireNonNull(user, "user is required");
        this.id = new AiSessionParticipantId(
                Objects.requireNonNull(session.getId(), "persisted session is required"),
                Objects.requireNonNull(user.getId(), "persisted user is required"));
        this.joinedAt = Objects.requireNonNull(joinedAt, "joinedAt is required");
        this.lastSeenAt = joinedAt;
    }

    public void touch(Instant now) {
        lastSeenAt = Objects.requireNonNull(now, "now is required");
    }

    public AiSessionParticipantId getId() { return id; }
    public AiSession getSession() { return session; }
    public User getUser() { return user; }
    public Instant getJoinedAt() { return joinedAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
}
