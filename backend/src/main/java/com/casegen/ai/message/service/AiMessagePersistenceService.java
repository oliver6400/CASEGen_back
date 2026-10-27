package com.casegen.ai.message.service;

import static com.casegen.ai.message.exception.AiMessageErrorCode.CLIENT_MESSAGE_ID_REUSED;
import static com.casegen.ai.message.exception.AiMessageErrorCode.INVALID_CONTEXT;
import static com.casegen.ai.message.exception.AiMessageErrorCode.SESSION_NOT_ACTIVE;
import static com.casegen.ai.message.exception.AiMessageErrorCode.SESSION_NOT_FOUND;

import com.casegen.ai.message.domain.AiMessage;
import com.casegen.ai.message.domain.AiMessageRole;
import com.casegen.ai.message.domain.AiSkill;
import com.casegen.ai.message.exception.AiMessageException;
import com.casegen.ai.message.repository.AiMessageRepository;
import com.casegen.ai.message.realtime.AiMessageEventPublisher;
import com.casegen.ai.provider.AiProviderResult;
import com.casegen.ai.session.domain.AiSession;
import com.casegen.ai.session.repository.AiSessionRepository;
import com.casegen.auth.domain.User;
import com.casegen.modeling.domain.Diagram;
import com.casegen.modeling.domain.UmlModel;
import com.casegen.modeling.repository.DiagramRepository;
import com.casegen.modeling.repository.UmlModelRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiMessagePersistenceService {
    private final AiMessageRepository messages;
    private final AiSessionRepository sessions;
    private final UmlModelRepository models;
    private final DiagramRepository diagrams;
    private final Clock clock;
    private final AiMessageEventPublisher events;

    public AiMessagePersistenceService(AiMessageRepository messages, AiSessionRepository sessions,
            UmlModelRepository models, DiagramRepository diagrams, Clock clock,
            AiMessageEventPublisher events) {
        this.messages = messages;
        this.sessions = sessions;
        this.models = models;
        this.diagrams = diagrams;
        this.clock = clock;
        this.events = events;
    }

    @Transactional
    public PreparedUserMessage persistOrReuseUser(UUID projectId, UUID sessionId, User actor,
            UUID clientMessageId, String content, UUID modelId, UUID diagramId) {
        return persistOrReuseUser(projectId, sessionId, actor, clientMessageId, content,
                modelId, diagramId, AiSkill.GENERAL);
    }

    @Transactional
    public PreparedUserMessage persistOrReuseUser(UUID projectId, UUID sessionId, User actor,
            UUID clientMessageId, String content, UUID modelId, UUID diagramId, AiSkill skill) {
        AiSession session = sessions.findByIdAndProjectIdForUpdate(sessionId, projectId)
                .orElseThrow(() -> error(SESSION_NOT_FOUND, "AI session not found"));
        if (!session.isActive()) throw error(SESSION_NOT_ACTIVE, "AI session is closed");

        var existing = messages.findBySession_IdAndClientMessageId(sessionId, clientMessageId);
        if (existing.isPresent()) {
            AiMessage user = existing.get();
            requireSameRequest(user, actor, content, modelId, diagramId, skill);
            AiMessage assistant = messages.findByReplyTo_IdAndRole(user.getId(), AiMessageRole.ASSISTANT)
                    .orElse(null);
            return new PreparedUserMessage(user, assistant, false);
        }

        UmlModel model = resolveModel(modelId, projectId);
        Diagram diagram = resolveDiagram(diagramId, model);
        AiMessage user = AiMessage.userMessage(session, actor, content, model, diagram,
                clientMessageId, skill, clock.instant());
        AiMessage persisted = messages.saveAndFlush(user);
        events.created(projectId, persisted);
        return new PreparedUserMessage(persisted, null, true);
    }

    @Transactional
    public AiMessage persistAssistant(UUID userMessageId, AiProviderResult result) {
        AiMessage user = messages.findDetailedById(userMessageId)
                .orElseThrow(() -> error(INVALID_CONTEXT, "User message no longer exists"));
        AiMessage existing = messages.findByReplyTo_IdAndRole(
                userMessageId, AiMessageRole.ASSISTANT).orElse(null);
        if (existing != null) return existing;
        AiMessage assistant = messages.saveAndFlush(AiMessage.assistantMessage(
                user.getSession(), result.content(), result.provider(), user.getModel(),
                user.getDiagram(), user, clock.instant()));
        events.created(user.getSession().getProject().getId(), assistant);
        return assistant;
    }

    @Transactional(readOnly = true)
    public AiMessage findAssistant(UUID userMessageId) {
        return messages.findByReplyTo_IdAndRole(userMessageId, AiMessageRole.ASSISTANT).orElse(null);
    }

    @Transactional(readOnly = true)
    public Page<AiMessage> history(UUID projectId, UUID sessionId, Pageable pageable) {
        if (sessions.findByIdAndProjectId(sessionId, projectId).isEmpty()) {
            throw error(SESSION_NOT_FOUND, "AI session not found");
        }
        return messages.findBySession_IdOrderByCreatedAtDescIdDesc(sessionId, pageable);
    }

    private UmlModel resolveModel(UUID modelId, UUID projectId) {
        if (modelId == null) return null;
        UmlModel model = models.findById(modelId)
                .orElseThrow(() -> error(INVALID_CONTEXT, "UML model not found"));
        if (!Objects.equals(model.getProject().getId(), projectId)) {
            throw error(INVALID_CONTEXT, "UML model does not belong to project");
        }
        return model;
    }

    private Diagram resolveDiagram(UUID diagramId, UmlModel model) {
        if (diagramId == null) return null;
        if (model == null) throw error(INVALID_CONTEXT, "Diagram context requires a UML model");
        Diagram diagram = diagrams.findById(diagramId)
                .orElseThrow(() -> error(INVALID_CONTEXT, "Diagram not found"));
        if (!Objects.equals(diagram.getModel().getId(), model.getId())) {
            throw error(INVALID_CONTEXT, "Diagram does not belong to UML model");
        }
        return diagram;
    }

    private void requireSameRequest(AiMessage message, User actor, String content,
            UUID modelId, UUID diagramId, AiSkill skill) {
        boolean same = message.getRole() == AiMessageRole.USER
                && Objects.equals(message.getAuthor().getId(), actor.getId())
                && Objects.equals(message.getContent(), content)
                && Objects.equals(id(message.getModel()), modelId)
                && Objects.equals(id(message.getDiagram()), diagramId)
                && message.getSkill() == skill;
        if (!same) throw error(CLIENT_MESSAGE_ID_REUSED,
                "clientMessageId was already used for a different message");
    }

    private UUID id(UmlModel model) { return model == null ? null : model.getId(); }
    private UUID id(Diagram diagram) { return diagram == null ? null : diagram.getId(); }
    private AiMessageException error(com.casegen.ai.message.exception.AiMessageErrorCode code,
            String message) { return new AiMessageException(code, message); }
}
