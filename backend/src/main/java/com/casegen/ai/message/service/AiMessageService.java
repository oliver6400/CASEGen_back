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
import com.casegen.interoperability.image.DetectedClassDiagram;
import com.casegen.interoperability.image.ImageImportExistingConfirmRequest;
import com.casegen.interoperability.image.ImageImportExistingConfirmationService;
import com.casegen.interoperability.image.ImageImportReconciliationPreviewRequest;
import com.casegen.interoperability.image.ImageImportReconciliationPreviewResponse;
import com.casegen.interoperability.image.ImageImportReconciliationPreviewResponse.Action;
import com.casegen.interoperability.image.ImageImportReconciliationPreviewService;
import com.casegen.interoperability.image.ImageImportService;
import com.casegen.project.service.ProjectAuthorizationService;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final ImageImportService imageImportService;
    private final ImageImportReconciliationPreviewService reconciliationPreview;
    private final ImageImportExistingConfirmationService existingConfirmation;
    // Coalesces identical requests only inside this Spring process. PostgreSQL constraints remain
    // the final persistence guard in multi-instance deployments; distributed exact-once provider
    // invocation would require a queue, outbox, or distributed lock outside B2-5.
    private final ConcurrentHashMap<MessageKey, InFlight> inFlight = new ConcurrentHashMap<>();

    public AiMessageService(ProjectAuthorizationService authorization,
            AiContextBuilder contextBuilder, SelectedAiProvider provider,
            AiMessagePersistenceService persistence,
            AiSessionGenerationCoordinator generationCoordinator,
            AiProviderProperties properties) {
        this(authorization, contextBuilder, provider, persistence, generationCoordinator, properties,
                null, null, null);
    }

    @Autowired
    public AiMessageService(ProjectAuthorizationService authorization,
            AiContextBuilder contextBuilder, SelectedAiProvider provider,
            AiMessagePersistenceService persistence,
            AiSessionGenerationCoordinator generationCoordinator,
            AiProviderProperties properties,
            @Autowired(required = false) ImageImportService imageImportService,
            @Autowired(required = false) ImageImportReconciliationPreviewService reconciliationPreview,
            @Autowired(required = false) ImageImportExistingConfirmationService existingConfirmation) {
        this.authorization = authorization;
        this.contextBuilder = contextBuilder;
        this.provider = provider;
        this.persistence = persistence;
        this.generationCoordinator = generationCoordinator;
        this.imageImportService = imageImportService;
        this.reconciliationPreview = reconciliationPreview;
        this.existingConfirmation = existingConfirmation;
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
            prepared = request.imageUrl() != null && !request.imageUrl().isBlank()
                    ? persistence.persistOrReuseUser(projectId, sessionId, actor,
                            request.clientMessageId(), request.content(), request.modelId(), request.diagramId(),
                            request.skill(), request.imageUrl())
                    : persistence.persistOrReuseUser(projectId, sessionId, actor,
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
                () -> {
                    if (prepared.userMessage().getImageUrl() != null && !prepared.userMessage().getImageUrl().isBlank()
                            && imageImportService != null) {
                        return generateAssistantForImage(projectId, sessionId, request, prepared.userMessage());
                    }
                    return generateAssistant(projectId, sessionId, request, prepared.userMessage());
                });
        return new SendAiMessageResult(prepared.userMessage(), assistant, !prepared.created());
    }

    private AiMessage generateAssistantForImage(UUID projectId, UUID sessionId,
            NormalizedRequest request, AiMessage userMessage) {
        try {
            byte[] bytes = decodeBase64Image(userMessage.getImageUrl());
            if (bytes == null || bytes.length == 0) {
                return persistence.persistAssistant(userMessage.getId(),
                        new AiProviderResult("No se pudo procesar la imagen adjunta. Verifica el formato.", "SYSTEM", "system", "STOP"));
            }
            String mediaType = detectMediaType(userMessage.getImageUrl());
            DetectedClassDiagram detected = imageImportService.analyze(projectId, bytes, mediaType, "chat-upload.png");

            if (request.modelId() != null && request.diagramId() != null && existingConfirmation != null && reconciliationPreview != null
                    && userMessage.getModel() != null && userMessage.getDiagram() != null) {
                var previewReq = new ImageImportReconciliationPreviewRequest(
                        request.modelId(), request.diagramId(), null,
                        userMessage.getModel().getRevision(), userMessage.getDiagram().getRevision(),
                        detected);
                var preview = reconciliationPreview.preview(projectId, previewReq);

                List<ImageImportExistingConfirmRequest.Decision> decisions = new ArrayList<>();
                for (var item : preview.items()) {
                    Action action = item.suggestedAction();
                    if (action == Action.CONFLICT) {
                        action = Action.CREATE;
                    }
                    UUID candidateId = (action == Action.MAP_TO_EXISTING && !item.candidates().isEmpty())
                            ? item.candidates().get(0).elementId()
                            : null;
                    decisions.add(new ImageImportExistingConfirmRequest.Decision(
                            item.temporaryId(), action, candidateId));
                }

                var confirmReq = new ImageImportExistingConfirmRequest(
                        request.modelId(), request.diagramId(), null,
                        userMessage.getModel().getRevision(), userMessage.getDiagram().getRevision(),
                        detected, decisions);

                var response = existingConfirmation.confirm(projectId, confirmReq);

                StringBuilder sb = new StringBuilder();
                sb.append("He analizado la imagen y extraído los elementos UML directamente en el lienzo:\n\n");
                sb.append("📊 **Resumen de elementos insertados:**\n");
                sb.append("- **Nuevos elementos creados:** ").append(response.createdElementCount()).append("\n");
                sb.append("- **Nodos en el diagrama:** ").append(response.createdNodeCount()).append("\n");
                sb.append("- **Relaciones agregadas:** ").append(response.createdEdgeCount()).append("\n\n");

                if (!detected.detectedClassifiers().isEmpty()) {
                    sb.append("🏷️ **Clases identificadas:**\n");
                    for (var c : detected.detectedClassifiers()) {
                        sb.append("- `").append(c.name()).append("` (").append(c.kind()).append(")\n");
                    }
                }
                if (!detected.warnings().isEmpty()) {
                    sb.append("\n⚠️ **Observaciones:**\n");
                    for (var w : detected.warnings()) {
                        sb.append("- ").append(w.message()).append("\n");
                    }
                }
                sb.append("\n✅ *El lienzo se ha actualizado en tiempo real con las nuevas clases y sus atributos.*");

                return persistence.persistAssistant(userMessage.getId(),
                        new AiProviderResult(sb.toString(), "OPENAI", "gpt-6-luna", "STOP"));
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append("He analizado la imagen y detectado los siguientes elementos UML:\n\n");
                for (var c : detected.detectedClassifiers()) {
                    sb.append("- **Clase:** `").append(c.name()).append("` (").append(c.kind()).append(")\n");
                }
                sb.append("\n*Abre un diagrama en el editor para que pueda insertar estos elementos directamente en el lienzo.*");
                return persistence.persistAssistant(userMessage.getId(),
                        new AiProviderResult(sb.toString(), "OPENAI", "gpt-6-luna", "STOP"));
            }
        } catch (Exception ex) {
            String errorMsg = "No se pudieron extraer elementos UML de la imagen adjunta: " +
                    (ex.getMessage() != null ? ex.getMessage() : "formato no reconocido o sin clases claras.") +
                    "\nAsegúrate de que la imagen sea legible y contenga nombres de clases y atributos.";
            return persistence.persistAssistant(userMessage.getId(),
                    new AiProviderResult(errorMsg, "SYSTEM", "system", "STOP"));
        }
    }

    private byte[] decodeBase64Image(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return null;
        String base64 = imageUrl;
        if (base64.contains(",")) {
            base64 = base64.substring(base64.indexOf(",") + 1);
        }
        return Base64.getDecoder().decode(base64.trim());
    }

    private String detectMediaType(String imageUrl) {
        if (imageUrl != null && imageUrl.startsWith("data:image/")) {
            int end = imageUrl.indexOf(";");
            if (end > 5) {
                return imageUrl.substring(5, end).toLowerCase(Locale.ROOT);
            }
        }
        return "image/png";
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
                request.modelId(), request.diagramId(), skill, request.imageUrl());
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
                                     UUID modelId, UUID diagramId, AiSkill skill, String imageUrl) { }
    private record InFlight(NormalizedRequest request,
                            CompletableFuture<SendAiMessageResult> result) { }
}
