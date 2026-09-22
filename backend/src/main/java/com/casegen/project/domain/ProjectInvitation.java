package com.casegen.project.domain;

import com.casegen.auth.domain.User;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_invitations")
public class ProjectInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_project_invitations_project"))
    private Project project;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invited_by", nullable = false,
            foreignKey = @ForeignKey(name = "fk_project_invitations_invited_by"))
    private User invitedBy;

    @Email
    @NotBlank
    @Size(max = 254)
    @Column(nullable = false, length = 254)
    private String email;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectInvitationRole role;

    @NotBlank
    @Size(max = 255)
    @Column(name = "token_hash", nullable = false, length = 255)
    private String tokenHash;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectInvitationStatus status = ProjectInvitationStatus.PENDING;

    @NotNull
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    protected ProjectInvitation() {
    }

    public ProjectInvitation(Project project, User invitedBy, String email, ProjectInvitationRole role,
            String tokenHash, Instant expiresAt) {
        this.project = project;
        this.invitedBy = invitedBy;
        this.email = email;
        this.role = role;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void accept() { status = ProjectInvitationStatus.ACCEPTED; acceptedAt = Instant.now(); }
    public void reject() { status = ProjectInvitationStatus.REJECTED; }
    public void revoke() { status = ProjectInvitationStatus.REVOKED; }

    public Project getProject() {
        return project;
    }

    public User getInvitedBy() {
        return invitedBy;
    }

    public String getEmail() {
        return email;
    }

    public ProjectInvitationRole getRole() {
        return role;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public ProjectInvitationStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }
}
