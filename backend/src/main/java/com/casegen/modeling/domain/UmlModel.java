package com.casegen.modeling.domain;

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
@Table(name = "uml_models", uniqueConstraints = {
        @UniqueConstraint(name = "uq_uml_models_project_name", columnNames = {"project_id", "name"})
})
public class UmlModel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_uml_models_project"))
    private Project project;

    @NotBlank
    @Size(max = 180)
    @Column(nullable = false, length = 180)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String documentation;

    @NotBlank
    @Size(max = 20)
    @Column(name = "uml_version", nullable = false, length = 20)
    private String umlVersion = "2.5.1";

    @PositiveOrZero
    @Column(nullable = false)
    private long revision;

    @Version
    @PositiveOrZero
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UmlModelStatus status = UmlModelStatus.EDITABLE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by",
            foreignKey = @ForeignKey(name = "fk_uml_models_created_by"))
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UmlModel() {
    }

    public UmlModel(Project project, String name, String documentation, User createdBy) {
        this.project = project;
        this.name = name;
        this.documentation = documentation;
        this.createdBy = createdBy;
    }

    public void recordSemanticMutation() {
        revision = Math.incrementExact(revision);
        updatedAt = Instant.now();
    }

    public void archive() {
        status = UmlModelStatus.ARCHIVED;
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
    public Project getProject() { return project; }
    public String getName() { return name; }
    public String getDocumentation() { return documentation; }
    public String getUmlVersion() { return umlVersion; }
    public long getRevision() { return revision; }
    public long getLockVersion() { return lockVersion; }
    public UmlModelStatus getStatus() { return status; }
    public User getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
