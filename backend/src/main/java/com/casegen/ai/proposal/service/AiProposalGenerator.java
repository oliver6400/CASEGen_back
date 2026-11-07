package com.casegen.ai.proposal.service;

import com.casegen.ai.message.domain.AiMessage;
import com.casegen.ai.message.domain.AiMessageRole;
import com.casegen.ai.message.domain.AiSkill;
import com.casegen.ai.message.repository.AiMessageRepository;
import com.casegen.ai.proposal.context.AiProposalContext;
import com.casegen.ai.proposal.context.AiProposalContextBuilder;
import com.casegen.ai.proposal.payload.AiProposalPayload;
import com.casegen.ai.proposal.payload.AiProposalPayloadParser;
import com.casegen.ai.proposal.validation.AiProposalPayloadValidator;
import com.casegen.ai.proposal.validation.AiProposalValidationException;
import com.casegen.ai.provider.AiProviderResult;
import com.casegen.ai.provider.AiProviderException;
import com.casegen.ai.provider.SelectedAiProvider;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AiProposalGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(AiProposalGenerator.class);
    private final AiMessageRepository messages;
    private final AiProposalContextBuilder contexts;
    private final AiProposalPromptBuilder prompts;
    private final SelectedAiProvider provider;
    private final AiProposalPayloadParser payloadParser;
    private final AiProposalPayloadValidator payloadValidator;
    private final JsonMapper strictJson;

    public AiProposalGenerator(AiMessageRepository messages, AiProposalContextBuilder contexts,
            AiProposalPromptBuilder prompts, SelectedAiProvider provider,
            AiProposalPayloadParser payloadParser, AiProposalPayloadValidator payloadValidator,
            JsonMapper json) {
        this.messages = messages;
        this.contexts = contexts;
        this.prompts = prompts;
        this.provider = provider;
        this.payloadParser = payloadParser;
        this.payloadValidator = payloadValidator;
        this.strictJson = json.rebuild()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    public GeneratedAiProposal generate(GenerateAiProposalCommand command) {
        requireCommand(command);
        AiMessage source = messages.findDetailedById(command.sourceAssistantMessageId())
                .orElseThrow(() -> error(AiProposalGenerationErrorCode.SOURCE_MESSAGE_NOT_FOUND,
                        "Source assistant message does not exist"));
        validateSource(command, source);

        UUID modelId = source.getModel().getId();
        UUID diagramId = source.getDiagram() == null ? null : source.getDiagram().getId();
        AiProposalContext context = contexts.build(command.projectId(), modelId, diagramId);
        AiProviderResult generated;
        try {
            generated = provider.generate(prompts.build(source, context));
        } catch (AiProviderException exception) {
            logFailure("PROVIDER", exception.getProvider(), provider.model(),
                    exception.getCode().name(), null, null, exception);
            throw exception;
        }
        GeneratedProposalEnvelope envelope = parseEnvelope(generated);
        AiProposalPayload parsed;
        try {
            parsed = payloadParser.parse(envelope.proposalType(), envelope.payload());
        } catch (AiProposalValidationException exception) {
            logFailure("PAYLOAD_PARSE", generated.provider(), provider.model(),
                    exception.getCode().name(), envelope.proposalType().name(), null, exception);
            throw exception;
        }
        AiProposalPayload validated;
        JsonNode canonical;
        try {
            validated = payloadValidator.validate(envelope.proposalType(), parsed, context);
            canonical = payloadParser.canonicalize(envelope.proposalType(), validated);
        } catch (AiProposalValidationException exception) {
            logFailure("PAYLOAD_VALIDATE", generated.provider(), provider.model(),
                    exception.getCode().name(), envelope.proposalType().name(), null, exception);
            throw exception;
        }

        return new GeneratedAiProposal(envelope.proposalType(), normalizeTitle(envelope.title()),
                normalizeRationale(envelope.rationale()), validated, canonical,
                context.modelRevision(), context.diagramRevision(), generated.provider(),
                AiSkill.UML_REVIEWER, modelId, diagramId, source.getId());
    }

    private GeneratedProposalEnvelope parseEnvelope(AiProviderResult generated) {
        String proposalType = null;
        java.util.List<String> rootFields = null;
        try {
            String raw = generated.content();
            if (raw != null && raw.trim().equals("NO_APPLICABLE_UML_PROPOSAL")) {
                throw error(AiProposalGenerationErrorCode.NO_APPLICABLE_UML_PROPOSAL,
                        "The source message does not contain an applicable UML proposal");
            }
            String jsonText = sanitize(raw);
            JsonNode root = strictJson.readTree(jsonText);
            if (root == null || !root.isObject()) {
                throw error(AiProposalGenerationErrorCode.INVALID_PROVIDER_RESPONSE,
                        "AI proposal response must be one JSON object");
            }
            rootFields = root.properties().stream().map(java.util.Map.Entry::getKey)
                    .sorted().toList();
            proposalType = root.path("proposalType").isTextual()
                    ? root.path("proposalType").asText() : null;
            GeneratedProposalEnvelope envelope = strictJson.treeToValue(
                    root, GeneratedProposalEnvelope.class);
            if (envelope.proposalType() == null || envelope.title() == null
                    || envelope.rationale() == null || envelope.payload() == null
                    || !envelope.payload().isObject()) {
                throw error(AiProposalGenerationErrorCode.INVALID_PROVIDER_RESPONSE,
                        "AI proposal response is missing required fields");
            }
            return envelope;
        } catch (AiProposalGenerationException exception) {
            if (exception.getCode() == AiProposalGenerationErrorCode.INVALID_PROVIDER_RESPONSE) {
                logFailure("ENVELOPE_PARSE", generated.provider(), provider.model(),
                        exception.getCode().name(), proposalType, rootFields, exception);
            }
            throw exception;
        } catch (RuntimeException exception) {
            AiProposalGenerationException wrapped = new AiProposalGenerationException(
                    AiProposalGenerationErrorCode.INVALID_PROVIDER_RESPONSE,
                    "AI proposal response does not match the strict JSON contract", exception);
            logFailure("ENVELOPE_PARSE", generated.provider(), provider.model(),
                    wrapped.getCode().name(), proposalType, rootFields, exception);
            throw wrapped;
        }
    }

    private void logFailure(String phase, String providerName, String model,
            String code, String proposalType, java.util.List<String> rootFields,
            RuntimeException exception) {
        LOGGER.warn("AI proposal generation failed provider={} model={} code={} phase={} "
                        + "proposalType={} rootFields={} exceptionType={}",
                providerName, model, code, phase, proposalType, rootFields,
                exception.getClass().getSimpleName());
    }

    private String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw error(AiProposalGenerationErrorCode.INVALID_PROVIDER_RESPONSE,
                    "AI proposal response is empty");
        }
        String value = raw.trim();
        if (value.startsWith("```json") && value.endsWith("```")) {
            value = value.substring(7, value.length() - 3).trim();
        } else if (value.startsWith("```") || value.endsWith("```")) {
            throw error(AiProposalGenerationErrorCode.INVALID_PROVIDER_RESPONSE,
                    "Only a complete json code fence can be sanitized");
        }
        return value;
    }

    private void validateSource(GenerateAiProposalCommand command, AiMessage source) {
        boolean valid = source.getRole() == AiMessageRole.ASSISTANT
                && source.getSkill() == AiSkill.UML_REVIEWER
                && source.getProvider() != null && !source.getProvider().isBlank()
                && source.getModel() != null && source.getSession() != null
                && source.getSession().getId().equals(command.sessionId())
                && source.getSession().getProject() != null
                && source.getSession().getProject().getId().equals(command.projectId());
        if (!valid) {
            throw error(AiProposalGenerationErrorCode.INVALID_SOURCE_MESSAGE,
                    "Source must be an ASSISTANT UML_REVIEWER message in the requested project and session with a model context");
        }
        if (source.getDiagram() != null
                && !source.getDiagram().getModel().getId().equals(source.getModel().getId())) {
            throw error(AiProposalGenerationErrorCode.INVALID_SOURCE_MESSAGE,
                    "Source diagram does not belong to its UML model context");
        }
    }

    private void requireCommand(GenerateAiProposalCommand command) {
        if (command == null || command.projectId() == null || command.sessionId() == null
                || command.sourceAssistantMessageId() == null) {
            throw error(AiProposalGenerationErrorCode.INVALID_COMMAND,
                    "projectId, sessionId and sourceAssistantMessageId are required");
        }
    }

    private String normalizeTitle(String value) {
        String normalized = normalizeText(value, "title");
        if (normalized.length() > 180) {
            throw error(AiProposalGenerationErrorCode.INVALID_PROVIDER_RESPONSE,
                    "Generated proposal title must not exceed 180 characters");
        }
        return normalized;
    }

    private String normalizeRationale(String value) {
        return normalizeText(value, "rationale");
    }

    private String normalizeText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw error(AiProposalGenerationErrorCode.INVALID_PROVIDER_RESPONSE,
                    "Generated proposal " + field + " must not be blank");
        }
        return value.trim();
    }

    private AiProposalGenerationException error(AiProposalGenerationErrorCode code,
            String message) {
        return new AiProposalGenerationException(code, message);
    }
}
