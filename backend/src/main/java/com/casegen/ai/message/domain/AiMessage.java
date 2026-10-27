package com.casegen.ai.message.domain;

import com.casegen.ai.session.domain.AiSession;
import com.casegen.auth.domain.User;
import com.casegen.modeling.domain.Diagram;
import com.casegen.modeling.domain.UmlModel;
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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "ai_messages")
public class AiMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ai_messages_session"))
    private AiSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_user_id",
            foreignKey = @ForeignKey(name = "fk_ai_messages_author_user"))
    private User author;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiMessageRole role;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AiSkill skill;

    @NotBlank
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_id", foreignKey = @ForeignKey(name = "fk_ai_messages_model"))
    private UmlModel model;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diagram_id", foreignKey = @ForeignKey(name = "fk_ai_messages_diagram"))
    private Diagram diagram;

    @Size(max = 30)
    @Column(length = 30)
    private String provider;

    @Column(name = "client_message_id")
    private UUID clientMessageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_message_id",
            foreignKey = @ForeignKey(name = "fk_ai_messages_reply_to"))
    private AiMessage replyTo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AiMessage() {
    }

    private AiMessage(AiSession session, User author, AiMessageRole role, AiSkill skill, String content,
            UmlModel model, Diagram diagram, String provider, UUID clientMessageId,
            AiMessage replyTo, Instant createdAt) {
        this.session = Objects.requireNonNull(session, "session is required");
        this.author = author;
        this.role = Objects.requireNonNull(role, "role is required");
        this.skill = Objects.requireNonNull(skill, "skill is required");
        this.content = requireText(content, "content");
        this.model = model;
        this.diagram = diagram;
        this.provider = provider;
        this.clientMessageId = clientMessageId;
        this.replyTo = replyTo;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
    }

    public static AiMessage userMessage(AiSession session, User author, String content,
            UmlModel model, Diagram diagram, UUID clientMessageId, Instant createdAt) {
        return userMessage(session, author, content, model, diagram, clientMessageId,
                AiSkill.GENERAL, createdAt);
    }

    public static AiMessage userMessage(AiSession session, User author, String content,
            UmlModel model, Diagram diagram, UUID clientMessageId, AiSkill skill, Instant createdAt) {
        return new AiMessage(session, Objects.requireNonNull(author, "author is required"),
                AiMessageRole.USER, skill, content, model, diagram, null, clientMessageId, null, createdAt);
    }

    public static AiMessage assistantMessage(AiSession session, String content, String provider,
            UmlModel model, Diagram diagram, AiMessage replyTo, Instant createdAt) {
        String validProvider = requireText(provider, "provider");
        if (replyTo != null && replyTo.getRole() != AiMessageRole.USER) {
            throw new IllegalArgumentException("replyTo must be a USER message");
        }
        if (replyTo != null && replyTo.getSession() != session) {
            throw new IllegalArgumentException("replyTo must belong to the same session");
        }
        AiSkill skill = replyTo == null ? AiSkill.GENERAL : replyTo.getSkill();
        return new AiMessage(session, null, AiMessageRole.ASSISTANT, skill, content, model, diagram,
                validProvider, null, replyTo, createdAt);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public UUID getId() { return id; }
    public AiSession getSession() { return session; }
    public User getAuthor() { return author; }
    public AiMessageRole getRole() { return role; }
    public AiSkill getSkill() { return skill; }
    public String getContent() { return content; }
    public UmlModel getModel() { return model; }
    public Diagram getDiagram() { return diagram; }
    public String getProvider() { return provider; }
    public UUID getClientMessageId() { return clientMessageId; }
    public AiMessage getReplyTo() { return replyTo; }
    public Instant getCreatedAt() { return createdAt; }
}
