package com.casegen.ai.message.service;

import static com.casegen.ai.message.exception.AiMessageErrorCode.CLIENT_MESSAGE_ID_REUSED;
import static com.casegen.ai.message.exception.AiMessageErrorCode.MESSAGE_INVALID;
import static com.casegen.ai.message.exception.AiMessageErrorCode.MESSAGE_TOO_LONG;

import com.casegen.ai.context.AiContextBuilder;
import com.casegen.ai.context.AiContextRequest;
import com.casegen.ai.message.domain.AiMessage;
import com.casegen.ai.message.domain.AiSkill;
import com.casegen.ai.message.dto.SendAiMessageRequest;
import com.casegen.ai.message.exception.AiMessageException;
import com.casegen.ai.provider.AiPrompt;
import com.casegen.ai.provider.AiProviderProperties;
import com.casegen.ai.provider.AiProviderResult;
import com.casegen.ai.provider.SelectedAiProvider;
import com.casegen.auth.domain.User;
import com.casegen.project.service.ProjectAuthorizationService;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class AiMessageService {
    private static final int MAX_PAGE_SIZE = 100;
    private final ProjectAuthorizationService authorization;
    private final AiContextBuilder contextBuilder;
    private final SelectedAiProvider provider;
    private final AiMessagePersistenceService persistence;
    private final AiSessionGenerationCoordinator generationCoordinator;
    private final int maxMessageLength;
    // Coalesces identical requests only inside this Spring process. PostgreSQL constraints remain
    // the final persistence guard in multi-instance deployments; distributed exact-once provider
    // invocation would require a queue, outbox, or distributed lock outside B2-5.
    private final ConcurrentHashMap<MessageKey, InFlight> inFlight = new ConcurrentHashMap<>();

    public AiMessageService(ProjectAuthorizationService authorization,
            AiContextBuilder contextBuilder, SelectedAiProvider provider,
            AiMessagePersistenceService persistence,
            AiSessionGenerationCoordinator generationCoordinator,
            AiProviderProperties properties) {
        this.authorization = authorization;
        this.contextBuilder = contextBuilder;
        this.provider = provider;
        this.persistence = persistence;
        this.generationCoordinator = generationCoordinator;
        this.maxMessageLength = properties.maxMessageLength() > 0
                ? properties.maxMessageLength() : 8000;
    }

    public SendAiMessageResult send(UUID projectId, UUID sessionId, SendAiMessageRequest request) {
        User actor = authorization.requireActiveMemberOrSuperuser(projectId);
        NormalizedRequest normalized = normalize(request, actor.getId());
        MessageKey key = new MessageKey(sessionId, normalized.clientMessageId());
        InFlight mine = new InFlight(normalized, new CompletableFuture<>());
        InFlight current = inFlight.putIfAbsent(key, mine);
        if (current != null) {
            if (!current.request().equals(normalized)) throw conflict();
            return await(current.result());
        }
        try {
            SendAiMessageResult result = generate(projectId, sessionId, actor, normalized);
            mine.result().complete(result);
            return result;
        } catch (Throwable failure) {
            mine.result().completeExceptionally(failure);
            if (failure instanceof RuntimeException runtime) throw runtime;
            if (failure instanceof Error error) throw error;
            throw new IllegalStateException(failure);
        } finally {
            inFlight.remove(key, mine);
        }
    }

    public Page<AiMessage> history(UUID projectId, UUID sessionId, int page, int size) {
        authorization.requireActiveMemberOrSuperuser(projectId);
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new AiMessageException(MESSAGE_INVALID,
                    "page must be at least 0 and size must be between 1 and 100");
        }
        return persistence.history(projectId, sessionId, PageRequest.of(page, size));
    }

    private SendAiMessageResult generate(UUID projectId, UUID sessionId, User actor,
            NormalizedRequest request) {
        PreparedUserMessage prepared;
        try {
            prepared = persistence.persistOrReuseUser(projectId, sessionId, actor,
                    request.clientMessageId(), request.content(), request.modelId(), request.diagramId(),
                    request.skill());
        } catch (DataIntegrityViolationException collision) {
            throw new AiMessageException(CLIENT_MESSAGE_ID_REUSED,
                    "Concurrent reuse of clientMessageId was detected");
        }

        if (prepared.assistantMessage() != null) {
            return new SendAiMessageResult(prepared.userMessage(), prepared.assistantMessage(), true);
        }
        AiMessage assistant = generationCoordinator.execute(sessionId,
                () -> generateAssistant(projectId, sessionId, request, prepared.userMessage()));
        return new SendAiMessageResult(prepared.userMessage(), assistant, !prepared.created());
    }

    private AiMessage generateAssistant(UUID projectId, UUID sessionId,
            NormalizedRequest request, AiMessage userMessage) {
        // The USER has already committed and been broadcast. Context is intentionally built only
        // after this session obtains its generation turn, so it includes earlier completed turns.
        AiPrompt prompt = contextBuilder.build(context(projectId, sessionId, request,
                userMessage.getId()));
        AiProviderResult generated = provider.generate(prompt);
        try {
            return persistence.persistAssistant(userMessage.getId(), generated);
        } catch (DataIntegrityViolationException collision) {
            AiMessage assistant = persistence.findAssistant(userMessage.getId());
            if (assistant == null) throw collision;
            return assistant;
        }
    }

    private AiContextRequest context(UUID projectId, UUID sessionId, NormalizedRequest request,
            UUID currentMessageId) {
        return new AiContextRequest(projectId, sessionId, request.modelId(), request.diagramId(),
                request.content(), currentMessageId, request.skill());
    }

    private NormalizedRequest normalize(SendAiMessageRequest request, UUID actorId) {
        if (request == null || request.clientMessageId() == null || request.content() == null
                || request.content().isBlank()) {
            throw new AiMessageException(MESSAGE_INVALID,
                    "clientMessageId and non-blank content are required");
        }
        String content = request.content().trim();
        if (content.length() > maxMessageLength) {
            throw new AiMessageException(MESSAGE_TOO_LONG, "Message exceeds configured limit");
        }
        if (request.diagramId() != null && request.modelId() == null) {
            throw new AiMessageException(MESSAGE_INVALID, "diagramId requires modelId");
        }
        AiSkill skill = request.skill() == null ? AiSkill.GENERAL : request.skill();
        return new NormalizedRequest(actorId, request.clientMessageId(), content,
                request.modelId(), request.diagramId(), skill);
    }

    private SendAiMessageResult await(CompletableFuture<SendAiMessageResult> future) {
        try {
            return future.join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw exception;
        }
    }

    private AiMessageException conflict() {
        return new AiMessageException(CLIENT_MESSAGE_ID_REUSED,
                "clientMessageId is concurrently used for a different message");
    }

    private record MessageKey(UUID sessionId, UUID clientMessageId) { }
    private record NormalizedRequest(UUID actorId, UUID clientMessageId, String content,
                                     UUID modelId, UUID diagramId, AiSkill skill) { }
    private record InFlight(NormalizedRequest request,
                            CompletableFuture<SendAiMessageResult> result) { }
}
