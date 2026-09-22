package com.casegen.modeling.domain;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "diagrams", uniqueConstraints = {
        @UniqueConstraint(name = "uq_diagrams_model_name", columnNames = {"model_id", "name"})
})
public class Diagram {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "model_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_diagrams_model"))
    private UmlModel model;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_package_id",
            foreignKey = @ForeignKey(name = "fk_diagrams_owner_package"))
    private UmlPackage ownerPackage;

    @NotBlank
    @Size(max = 180)
    @Column(nullable = false, length = 180)
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DiagramType type;

    @PositiveOrZero
    @Column(nullable = false)
    private long revision;

    @Version
    @PositiveOrZero
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", foreignKey = @ForeignKey(name = "fk_diagrams_created_by"))
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Diagram() {
    }

    public Diagram(UmlModel model, UmlPackage ownerPackage, String name, DiagramType type, User createdBy) {
        this.model = model;
        this.ownerPackage = ownerPackage;
        this.name = name;
        this.type = type;
        this.createdBy = createdBy;
    }

    public void recordNotationMutation() {
        revision = Math.incrementExact(revision);
        updatedAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UmlModel getModel() { return model; }
    public UmlPackage getOwnerPackage() { return ownerPackage; }
    public String getName() { return name; }
    public DiagramType getType() { return type; }
    public long getRevision() { return revision; }
    public long getLockVersion() { return lockVersion; }
    public User getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
