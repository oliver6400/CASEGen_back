package com.casegen.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "system_bootstrap")
public class SystemBootstrap {

    public static final short SINGLETON_ID = 1;

    @Id
    private Short id;

    @Column(name = "superuser_created", nullable = false)
    private boolean superuserCreated;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "superuser_user_id",
            foreignKey = @ForeignKey(name = "fk_system_bootstrap_superuser"))
    private User superuserUser;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected SystemBootstrap() {
    }

    public void completeWith(User user, Instant instant) {
        if (superuserCreated) {
            throw new IllegalStateException("Superuser bootstrap is already complete");
        }
        superuserCreated = true;
        superuserUser = user;
        completedAt = instant;
    }

    public Short getId() {
        return id;
    }

    public boolean isSuperuserCreated() {
        return superuserCreated;
    }

    public User getSuperuserUser() {
        return superuserUser;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
