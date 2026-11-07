package com.casegen.ai.proposal.domain;

import com.casegen.ai.message.domain.AiMessage;
import com.casegen.ai.message.domain.AiMessageRole;
import com.casegen.ai.message.domain.AiSkill;
import com.casegen.ai.session.domain.AiSession;
import com.casegen.auth.domain.User;
import com.casegen.modeling.domain.Diagram;
import com.casegen.modeling.domain.UmlModel;
import com.casegen.project.domain.Project;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "ai_proposal_batches", uniqueConstraints = @UniqueConstraint(
        name = "uq_ai_proposal_batches_project_client", columnNames = {"project_id", "client_batch_id"}))
public class AiProposalBatch {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @NotNull @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false) private Project project;
    @NotNull @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false) private AiSession session;
    @NotNull @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_message_id", nullable = false) private AiMessage sourceMessage;
    @NotNull @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by_user_id", nullable = false) private User requestedBy;
    @NotNull @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "model_id", nullable = false) private UmlModel model;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "diagram_id") private Diagram diagram;
    @NotBlank @Size(max = 180) @Column(nullable = false, length = 180) private String title;
    @NotBlank @Column(nullable = false, columnDefinition = "TEXT") private String summary;
    @PositiveOrZero @Column(name = "base_model_revision", nullable = false) private long baseModelRevision;
    @PositiveOrZero @Column(name = "base_diagram_revision") private Long baseDiagramRevision;
    @PositiveOrZero @Column(name = "model_revision_cursor", nullable = false) private long modelRevisionCursor;
    @NotBlank @Size(max = 30) @Column(nullable = false, length = 30) private String provider;
    @NotNull @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private AiSkill skill;
    @NotNull @Column(name = "client_batch_id", nullable = false) private UUID clientBatchId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "applied_by_user_id") private User appliedBy;
    @Column(name = "applied_at") private Instant appliedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(name = "lock_version", nullable = false) private long lockVersion;

    protected AiProposalBatch() { }

    public static AiProposalBatch create(Project project, AiSession session, AiMessage sourceMessage,
            User requestedBy, UmlModel model, Diagram diagram, String title, String summary,
            long baseModelRevision, Long baseDiagramRevision, String provider, UUID clientBatchId) {
        AiProposalBatch value = new AiProposalBatch();
        value.project = Objects.requireNonNull(project, "project is required");
        value.session = Objects.requireNonNull(session, "session is required");
        value.sourceMessage = Objects.requireNonNull(sourceMessage, "sourceMessage is required");
        value.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy is required");
        value.model = Objects.requireNonNull(model, "model is required");
        value.diagram = diagram;
        value.title = text(title, "title", 180);
        value.summary = text(summary, "summary", Integer.MAX_VALUE);
        if (baseModelRevision < 0) throw new IllegalArgumentException("baseModelRevision must not be negative");
        if (baseDiagramRevision != null && baseDiagramRevision < 0) {
            throw new IllegalArgumentException("baseDiagramRevision must not be negative");
        }
        if (diagram == null && baseDiagramRevision != null || diagram != null && baseDiagramRevision == null) {
            throw new IllegalArgumentException("baseDiagramRevision must match diagram presence");
        }
        value.baseModelRevision = baseModelRevision;
        value.baseDiagramRevision = baseDiagramRevision;
        value.modelRevisionCursor = baseModelRevision;
        value.provider = text(provider, "provider", 30);
        value.skill = sourceMessage.getSkill();
        value.clientBatchId = Objects.requireNonNull(clientBatchId, "clientBatchId is required");
        value.validateContext();
        return value;
    }

    public void advanceModelRevisionCursor(long expectedCurrent, long newRevision) {
        if (modelRevisionCursor != expectedCurrent) throw new IllegalStateException("model revision cursor changed");
        if (newRevision < expectedCurrent) throw new IllegalArgumentException("model revision cursor cannot move backwards");
        modelRevisionCursor = newRevision;
    }

    public void recordApplyAll(User actor, Instant occurredAt) {
        this.appliedBy = Objects.requireNonNull(actor, "appliedBy is required");
        this.appliedAt = Objects.requireNonNull(occurredAt, "appliedAt is required");
    }

    private void validateContext() {
        if (sourceMessage.getRole() != AiMessageRole.ASSISTANT) throw new IllegalArgumentException("sourceMessage must be ASSISTANT");
        if (sourceMessage.getSkill() != AiSkill.UML_REVIEWER) throw new IllegalArgumentException("sourceMessage must use UML_REVIEWER");
        same(project, session.getProject(), "session must belong to project");
        same(session, sourceMessage.getSession(), "sourceMessage must belong to session");
        same(project, model.getProject(), "model must belong to project");
        same(model, sourceMessage.getModel(), "model must match sourceMessage context");
        same(diagram, sourceMessage.getDiagram(), "diagram must match sourceMessage context");
        if (diagram != null) same(model, diagram.getModel(), "diagram must belong to model");
    }

    private static void same(Object expected, Object actual, String message) {
        if (expected != actual) throw new IllegalArgumentException(message);
    }
    private static String text(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(field + " is too long");
        return normalized;
    }
    @PrePersist void created() { Instant now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void updated() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public Project getProject() { return project; }
    public AiSession getSession() { return session; }
    public AiMessage getSourceMessage() { return sourceMessage; }
    public User getRequestedBy() { return requestedBy; }
    public UmlModel getModel() { return model; }
    public Diagram getDiagram() { return diagram; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public long getBaseModelRevision() { return baseModelRevision; }
    public Long getBaseDiagramRevision() { return baseDiagramRevision; }
    public long getModelRevisionCursor() { return modelRevisionCursor; }
    public String getProvider() { return provider; }
    public AiSkill getSkill() { return skill; }
    public UUID getClientBatchId() { return clientBatchId; }
    public User getAppliedBy() { return appliedBy; }
    public Instant getAppliedAt() { return appliedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getLockVersion() { return lockVersion; }
}
