package com.casegen.ai.session.domain;

import com.casegen.auth.domain.User;
import com.casegen.project.domain.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "ai_sessions")
public class AiSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ai_sessions_project"))
    private Project project;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ai_sessions_host_user"))
    private User host;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiSessionStatus status = AiSessionStatus.ACTIVE;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "session")
    private List<AiSessionParticipant> participants = new ArrayList<>();

    protected AiSession() {
    }

    public AiSession(Project project, User host, Instant startedAt) {
        this.project = Objects.requireNonNull(project, "project is required");
        this.host = Objects.requireNonNull(host, "host is required");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt is required");
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (startedAt == null) startedAt = now;
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void close(Instant now) {
        Objects.requireNonNull(now, "now is required");
        if (!isActive()) return;
        status = AiSessionStatus.CLOSED;
        endedAt = now;
    }

    public boolean isActive() {
        return status == AiSessionStatus.ACTIVE;
    }

    public void addParticipant(AiSessionParticipant participant) {
        participants.add(Objects.requireNonNull(participant, "participant is required"));
    }

    public UUID getId() { return id; }
    public Project getProject() { return project; }
    public User getHost() { return host; }
    public AiSessionStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<AiSessionParticipant> getParticipants() {
        return Collections.unmodifiableList(participants);
    }
}
