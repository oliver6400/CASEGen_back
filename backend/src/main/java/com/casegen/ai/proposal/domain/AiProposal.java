package com.casegen.ai.proposal.domain;

import com.casegen.ai.message.domain.AiMessage;
import com.casegen.ai.message.domain.AiMessageRole;
import com.casegen.ai.message.domain.AiSkill;
import com.casegen.ai.session.domain.AiSession;
import com.casegen.auth.domain.User;
import com.casegen.modeling.domain.Diagram;
import com.casegen.modeling.domain.UmlModel;
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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

@Entity
@Table(name = "ai_proposals", uniqueConstraints = {
        @UniqueConstraint(name = "uq_ai_proposals_project_client",
                columnNames = {"project_id", "client_proposal_id"})
})
public class AiProposal {

    private static final int MAX_DECISION_NOTE_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ai_proposals_project"))
    private Project project;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ai_proposals_session"))
    private AiSession session;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_message_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ai_proposals_source_message"))
    private AiMessage sourceMessage;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by_user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ai_proposals_requested_by"))
    private User requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_id", foreignKey = @ForeignKey(name = "fk_ai_proposals_model"))
    private UmlModel model;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diagram_id", foreignKey = @ForeignKey(name = "fk_ai_proposals_diagram"))
    private Diagram diagram;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiProposalStatus status;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "proposal_type", nullable = false, length = 40)
    private AiProposalType proposalType;

    @NotBlank
    @Size(max = 180)
    @Column(nullable = false, length = 180)
    private String title;

    @NotBlank
    @Column(nullable = false, columnDefinition = "TEXT")
    private String rationale;

    @NotNull
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode payload;

    @Positive
    @Column(name = "payload_schema_version", nullable = false)
    private short payloadSchemaVersion;

    @PositiveOrZero
    @Column(name = "base_model_revision")
    private Long baseModelRevision;

    @PositiveOrZero
    @Column(name = "base_diagram_revision")
    private Long baseDiagramRevision;

    @NotBlank
    @Size(max = 30)
    @Column(nullable = false, length = 30)
    private String provider;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AiSkill skill;

    @NotNull
    @Column(name = "client_proposal_id", nullable = false)
    private UUID clientProposalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_user_id",
            foreignKey = @ForeignKey(name = "fk_ai_proposals_decided_by"))
    private User decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Size(max = MAX_DECISION_NOTE_LENGTH)
    @Column(name = "decision_note", columnDefinition = "TEXT")
    private String decisionNote;

    @PositiveOrZero
    @Column(name = "applied_model_revision")
    private Long appliedModelRevision;

    @Column(name = "applied_element_id")
    private UUID appliedElementId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", foreignKey = @ForeignKey(name = "fk_ai_proposals_batch"))
    private AiProposalBatch batch;

    @Column(name = "batch_order")
    private Integer batchOrder;

    @Size(max = 80)
    @Column(name = "client_ref", length = 80)
    private String clientRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AiProposal() {
    }

    private AiProposal(Project project, AiSession session, AiMessage sourceMessage, User requestedBy,
            UmlModel model, Diagram diagram, AiProposalType proposalType, String title,
            String rationale, JsonNode payload, short payloadSchemaVersion,
            Long baseModelRevision, Long baseDiagramRevision, String generatedByProvider,
            UUID clientProposalId) {
        this.project = Objects.requireNonNull(project, "project is required");
        this.session = Objects.requireNonNull(session, "session is required");
        this.sourceMessage = requireSource(sourceMessage);
        this.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy is required");
        this.model = model;
        this.diagram = diagram;
        this.status = AiProposalStatus.PENDING;
        this.proposalType = Objects.requireNonNull(proposalType, "proposalType is required");
        this.title = requireText(title, "title", 180);
        this.rationale = requireText(rationale, "rationale", Integer.MAX_VALUE);
        this.payload = requirePayload(payload).deepCopy();
        if (payloadSchemaVersion <= 0) {
            throw new IllegalArgumentException("payloadSchemaVersion must be greater than zero");
        }
        this.payloadSchemaVersion = payloadSchemaVersion;
        this.baseModelRevision = requireRevision(model, baseModelRevision, "baseModelRevision");
        this.baseDiagramRevision = requireRevision(diagram, baseDiagramRevision, "baseDiagramRevision");
        this.provider = requireText(generatedByProvider, "generatedByProvider", 30);
        this.skill = sourceMessage.getSkill();
        this.clientProposalId = Objects.requireNonNull(clientProposalId, "clientProposalId is required");
        validateContext();
    }

    public static AiProposal pending(Project project, AiSession session, AiMessage sourceMessage,
            User requestedBy, UmlModel model, Diagram diagram, AiProposalType proposalType,
            String title, String rationale, JsonNode payload, short payloadSchemaVersion,
            Long baseModelRevision, Long baseDiagramRevision, String generatedByProvider,
            UUID clientProposalId) {
        if (proposalType == AiProposalType.UPDATE_CLASSIFIER
                || proposalType == AiProposalType.UPDATE_ATTRIBUTE) {
            throw new IllegalArgumentException("UPDATE proposals require review payload schema 2 and a batch");
        }
        return new AiProposal(project, session, sourceMessage, requestedBy, model, diagram,
                proposalType, title, rationale, payload, payloadSchemaVersion,
                baseModelRevision, baseDiagramRevision, generatedByProvider, clientProposalId);
    }

    public static AiProposal pendingInBatch(AiProposalBatch batch, int batchOrder, String clientRef,
            AiProposalType proposalType, String title, String rationale, JsonNode payload,
            short payloadSchemaVersion, UUID clientProposalId) {
        Objects.requireNonNull(batch, "batch is required");
        if (batchOrder < 1 || batchOrder > 15) {
            throw new IllegalArgumentException("batchOrder must be between 1 and 15");
        }
        String normalizedRef = requireText(clientRef, "clientRef", 80);
        if (!normalizedRef.matches("[a-z0-9][a-z0-9-]*")) {
            throw new IllegalArgumentException("clientRef has an invalid format");
        }
        AiProposal proposal = new AiProposal(batch.getProject(), batch.getSession(),
                batch.getSourceMessage(), batch.getRequestedBy(), batch.getModel(), batch.getDiagram(),
                proposalType, title, rationale, payload, payloadSchemaVersion,
                batch.getBaseModelRevision(), batch.getBaseDiagramRevision(), batch.getProvider(),
                clientProposalId);
        proposal.batch = batch;
        proposal.batchOrder = batchOrder;
        proposal.clientRef = normalizedRef;
        return proposal;
    }

    private static AiMessage requireSource(AiMessage source) {
        Objects.requireNonNull(source, "sourceMessage is required");
        if (source.getRole() != AiMessageRole.ASSISTANT) {
            throw new IllegalArgumentException("sourceMessage must be an ASSISTANT message");
        }
        if (source.getSkill() != AiSkill.UML_REVIEWER) {
            throw new IllegalArgumentException("sourceMessage must use UML_REVIEWER");
        }
        requireText(source.getProvider(), "sourceMessage.provider", 30);
        return source;
    }

    private void validateContext() {
        requireSame(project, session.getProject(), "session must belong to project");
        requireSame(session, sourceMessage.getSession(), "sourceMessage must belong to session");
        requireSame(model, sourceMessage.getModel(), "model must match sourceMessage context");
        requireSame(diagram, sourceMessage.getDiagram(), "diagram must match sourceMessage context");
        if (model != null) requireSame(project, model.getProject(), "model must belong to project");
        if (diagram != null) {
            if (model == null) throw new IllegalArgumentException("diagram requires model");
            requireSame(model, diagram.getModel(), "diagram must belong to model");
        }
    }

    private static JsonNode requirePayload(JsonNode value) {
        if (value == null) throw new IllegalArgumentException("payload is required");
        if (!value.isObject()) throw new IllegalArgumentException("payload must be a JSON object");
        return value;
    }

    private static Long requireRevision(Object context, Long revision, String field) {
        if (context == null && revision != null) {
            throw new IllegalArgumentException(field + " requires its context");
        }
        if (context != null && revision == null) {
            throw new IllegalArgumentException(field + " is required for its context");
        }
        if (revision != null && revision < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return revision;
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds maximum length " + maxLength);
        }
        return normalized;
    }

    public void applyDecision(User decidedBy, Instant decidedAt, String note,
            long appliedModelRevision, UUID appliedElementId) {
        requirePending();
        if (appliedModelRevision < 0) {
            throw new IllegalArgumentException("appliedModelRevision must not be negative");
        }
        User actor = Objects.requireNonNull(decidedBy, "decidedBy is required");
        Instant decisionTime = Objects.requireNonNull(decidedAt, "decidedAt is required");
        String normalizedNote = normalizeDecisionNote(note);
        UUID elementId = Objects.requireNonNull(appliedElementId, "appliedElementId is required");
        this.decidedBy = actor;
        this.decidedAt = decisionTime;
        this.decisionNote = normalizedNote;
        this.appliedModelRevision = appliedModelRevision;
        this.appliedElementId = elementId;
        this.status = AiProposalStatus.APPLIED;
    }

    public void rejectDecision(User decidedBy, Instant decidedAt, String note) {
        requirePending();
        User actor = Objects.requireNonNull(decidedBy, "decidedBy is required");
        Instant decisionTime = Objects.requireNonNull(decidedAt, "decidedAt is required");
        String normalizedNote = normalizeDecisionNote(note);
        this.decidedBy = actor;
        this.decidedAt = decisionTime;
        this.decisionNote = normalizedNote;
        this.appliedModelRevision = null;
        this.appliedElementId = null;
        this.status = AiProposalStatus.REJECTED;
    }

    private void requirePending() {
        if (status != AiProposalStatus.PENDING) {
            throw new AiProposalDecisionException("AI proposal has already been decided");
        }
    }

    private static String normalizeDecisionNote(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > MAX_DECISION_NOTE_LENGTH) {
            throw new IllegalArgumentException(
                    "decisionNote exceeds maximum length " + MAX_DECISION_NOTE_LENGTH);
        }
        return normalized;
    }

    private static void requireSame(Object expected, Object actual, String message) {
        if (expected == actual) return;
        if (expected == null || actual == null) throw new IllegalArgumentException(message);
        UUID expectedId = entityId(expected);
        UUID actualId = entityId(actual);
        if (expectedId == null || !expectedId.equals(actualId)) throw new IllegalArgumentException(message);
    }

    private static UUID entityId(Object entity) {
        if (entity instanceof Project value) return value.getId();
        if (entity instanceof AiSession value) return value.getId();
        if (entity instanceof UmlModel value) return value.getId();
        if (entity instanceof Diagram value) return value.getId();
        return null;
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
    public AiSession getSession() { return session; }
    public AiMessage getSourceMessage() { return sourceMessage; }
    public User getRequestedBy() { return requestedBy; }
    public UmlModel getModel() { return model; }
    public Diagram getDiagram() { return diagram; }
    public AiProposalStatus getStatus() { return status; }
    public AiProposalType getProposalType() { return proposalType; }
    public String getTitle() { return title; }
    public String getRationale() { return rationale; }
    public JsonNode getPayload() { return payload.deepCopy(); }
    public short getPayloadSchemaVersion() { return payloadSchemaVersion; }
    public Long getBaseModelRevision() { return baseModelRevision; }
    public Long getBaseDiagramRevision() { return baseDiagramRevision; }
    public String getProvider() { return provider; }
    public AiSkill getSkill() { return skill; }
    public UUID getClientProposalId() { return clientProposalId; }
    public User getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public String getDecisionNote() { return decisionNote; }
    public Long getAppliedModelRevision() { return appliedModelRevision; }
    public UUID getAppliedElementId() { return appliedElementId; }
    public AiProposalBatch getBatch() { return batch; }
    public Integer getBatchOrder() { return batchOrder; }
    public String getClientRef() { return clientRef; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
