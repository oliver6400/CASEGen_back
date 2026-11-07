package com.casegen.ai.proposal.payload;

import com.casegen.ai.proposal.domain.AiProposalType;
import com.casegen.ai.proposal.validation.AiProposalValidationErrorCode;
import com.casegen.ai.proposal.validation.AiProposalValidationException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class AiProposalPayloadParser {
    private final JsonMapper mapper;

    public AiProposalPayloadParser(JsonMapper objectMapper) {
        this.mapper = objectMapper.rebuild()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
    }

    public AiProposalPayload parse(AiProposalType type, JsonNode json) {
        if (type == null || json == null || !json.isObject()) {
            throw invalid("Proposal payload must be a JSON object", null);
        }
        try {
            AiProposalPayload payload = mapper.treeToValue(json, payloadClass(type));
            requireRequiredFields(type, payload);
            return payload;
        } catch (RuntimeException exception) {
            boolean unknown = isUnknownField(exception);
            throw new AiProposalValidationException(
                    unknown ? AiProposalValidationErrorCode.UNKNOWN_PAYLOAD_FIELD
                            : AiProposalValidationErrorCode.INVALID_PAYLOAD,
                    unknown ? "Proposal payload contains an unknown field"
                            : "Proposal payload has an invalid structure",
                    exception);
        }
    }

    private void requireRequiredFields(AiProposalType type, AiProposalPayload payload) {
        boolean complete = switch (type) {
            case ADD_CLASSIFIER -> {
                AddClassifierPayload value = (AddClassifierPayload) payload;
                yield value.kind() != null && value.name() != null && value.visibility() != null
                        && value.abstractClassifier() != null && value.active() != null;
            }
            case ADD_ATTRIBUTE -> {
                AddAttributePayload value = (AddAttributePayload) payload;
                yield value.ownerClassifierId() != null && value.name() != null
                        && value.visibility() != null && value.typeKind() != null
                        && value.lowerBound() != null && value.staticProperty() != null
                        && value.readOnly() != null && value.derived() != null;
            }
            case ADD_OPERATION -> {
                AddOperationPayload value = (AddOperationPayload) payload;
                yield value.ownerClassifierId() != null && value.name() != null
                        && value.visibility() != null && value.abstractOperation() != null
                        && value.staticOperation() != null && value.query() != null;
            }
            case ADD_ASSOCIATION -> {
                AddAssociationPayload value = (AddAssociationPayload) payload;
                yield completeEnd(value.firstEnd()) && completeEnd(value.secondEnd());
            }
            case ADD_GENERALIZATION -> {
                AddGeneralizationPayload value = (AddGeneralizationPayload) payload;
                yield value.specificClassifierId() != null && value.generalClassifierId() != null
                        && value.substitutable() != null;
            }
            case ADD_REALIZATION -> {
                AddRealizationPayload value = (AddRealizationPayload) payload;
                yield value.clientClassifierId() != null && value.supplierClassifierId() != null;
            }
            case UPDATE_CLASSIFIER, UPDATE_ATTRIBUTE -> throw invalid(
                    "Review update proposals require payload schema 2", null);
        };
        if (!complete) {
            throw invalid("Proposal payload is missing a required field", null);
        }
    }

    private boolean completeEnd(AssociationEndProposalPayload value) {
        return value != null && value.classifierId() != null && value.lowerBound() != null
                && value.navigable() != null && value.aggregation() != null
                && value.ownership() != null;
    }

    private boolean isUnknownField(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current.getClass().getSimpleName().contains("UnrecognizedProperty")
                    || current.getMessage() != null
                    && current.getMessage().contains("Unrecognized field")) {
                return true;
            }
        }
        return false;
    }

    public JsonNode canonicalize(AiProposalType type, AiProposalPayload payload) {
        requireCompatible(type, payload);
        return mapper.valueToTree(payload);
    }

    private void requireCompatible(AiProposalType type, AiProposalPayload payload) {
        if (type == null || payload == null || !payloadClass(type).isInstance(payload)) {
            throw invalid("Proposal type and payload are incompatible", null);
        }
    }

    private Class<? extends AiProposalPayload> payloadClass(AiProposalType type) {
        return switch (type) {
            case ADD_CLASSIFIER -> AddClassifierPayload.class;
            case ADD_ATTRIBUTE -> AddAttributePayload.class;
            case ADD_OPERATION -> AddOperationPayload.class;
            case ADD_ASSOCIATION -> AddAssociationPayload.class;
            case ADD_GENERALIZATION -> AddGeneralizationPayload.class;
            case ADD_REALIZATION -> AddRealizationPayload.class;
            case UPDATE_CLASSIFIER, UPDATE_ATTRIBUTE -> throw invalid(
                    "Review update proposals require payload schema 2", null);
        };
    }

    private AiProposalValidationException invalid(String message, Throwable cause) {
        return new AiProposalValidationException(
                AiProposalValidationErrorCode.INVALID_PAYLOAD, message, cause);
    }
}
