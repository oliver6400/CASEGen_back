package com.casegen.ai.proposal.service;

import com.casegen.ai.proposal.domain.AiProposalType;
import com.casegen.ai.proposal.review.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.casegen.modeling.domain.TypeKind;

@Component
public class AiProposalBatchPayloadParser {
    private final JsonMapper mapper;

    public AiProposalBatchPayloadParser(JsonMapper mapper) {
        this.mapper = mapper.rebuild()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();
    }

    public AiReviewPayload parse(AiProposalType type, JsonNode payload) {
        try {
            if (payload == null || !payload.isObject()) throw new IllegalArgumentException();
            AiReviewPayload parsed = switch (type) {
                case ADD_CLASSIFIER -> mapper.treeToValue(payload, ReviewAddClassifierPayload.class);
                case ADD_ATTRIBUTE -> mapper.treeToValue(payload, ReviewAddAttributePayload.class);
                case ADD_OPERATION -> mapper.treeToValue(payload, ReviewAddOperationPayload.class);
                case ADD_ASSOCIATION -> mapper.treeToValue(payload, ReviewAddAssociationPayload.class);
                case ADD_GENERALIZATION -> mapper.treeToValue(payload, ReviewAddGeneralizationPayload.class);
                case ADD_REALIZATION -> mapper.treeToValue(payload, ReviewAddRealizationPayload.class);
                case UPDATE_CLASSIFIER -> mapper.treeToValue(payload, ReviewUpdateClassifierPayload.class);
                case UPDATE_ATTRIBUTE -> mapper.treeToValue(payload, ReviewUpdateAttributePayload.class);
            };
            validate(type, parsed);
            return parsed;
        } catch (RuntimeException exception) {
            throw new AiProposalException(AiProposalErrorCode.INVALID_BATCH_PROPOSAL_PAYLOAD,
                    "Batch proposal payload does not match schema 2", exception);
        }
    }

    private void validate(AiProposalType type, AiReviewPayload payload) {
        boolean valid = switch (type) {
            case ADD_CLASSIFIER -> {
                var p = (ReviewAddClassifierPayload) payload;
                yield p.name() != null && p.kind() != null && p.visibility() != null
                        && p.abstractClassifier() != null && p.active() != null;
            }
            case ADD_ATTRIBUTE -> {
                var p = (ReviewAddAttributePayload) payload;
                yield p.ownerRef() != null && p.name() != null && p.visibility() != null
                        && typed(p.typeKind(), p.primitiveType(), p.typeClassifierRef())
                        && multiplicity(p.lowerBound(), p.upperBound())
                        && p.staticProperty() != null && p.readOnly() != null && p.derived() != null;
            }
            case ADD_OPERATION -> {
                var p = (ReviewAddOperationPayload) payload;
                yield p.ownerRef() != null && p.name() != null && p.visibility() != null
                        && p.abstractOperation() != null && p.staticOperation() != null
                        && p.query() != null;
            }
            case ADD_ASSOCIATION -> {
                var p = (ReviewAddAssociationPayload) payload;
                yield end(p.firstEnd()) && end(p.secondEnd());
            }
            case ADD_GENERALIZATION -> {
                var p = (ReviewAddGeneralizationPayload) payload;
                yield p.specificRef() != null && p.generalRef() != null
                        && p.substitutable() != null;
            }
            case ADD_REALIZATION -> {
                var p = (ReviewAddRealizationPayload) payload;
                yield p.clientClassifierRef() != null && p.supplierRef() != null;
            }
            case UPDATE_CLASSIFIER -> {
                var p = (ReviewUpdateClassifierPayload) payload;
                yield p.targetClassifierId() != null && p.name() != null
                        && p.visibility() != null && p.abstractClassifier() != null
                        && p.active() != null;
            }
            case UPDATE_ATTRIBUTE -> {
                var p = (ReviewUpdateAttributePayload) payload;
                yield p.targetPropertyId() != null && p.name() != null
                        && p.visibility() != null
                        && typed(p.typeKind(), p.primitiveType(), p.typeClassifierRef())
                        && multiplicity(p.lowerBound(), p.upperBound())
                        && p.staticProperty() != null && p.readOnly() != null && p.derived() != null;
            }
        };
        if (!valid) throw new IllegalArgumentException("missing or incompatible schema-2 fields");
    }

    private boolean typed(TypeKind kind, Object primitive, ReviewElementRef classifier) {
        return kind == TypeKind.PRIMITIVE && primitive != null && classifier == null
                || kind == TypeKind.CLASSIFIER && primitive == null && classifier != null;
    }

    private boolean multiplicity(Integer lower, Integer upper) {
        return lower != null && lower >= 0 && (upper == null || upper >= lower);
    }

    private boolean end(ReviewAssociationEndPayload value) {
        return value != null && value.classifierRef() != null
                && multiplicity(value.lowerBound(), value.upperBound())
                && value.navigable() != null && value.aggregation() != null
                && value.ownership() != null;
    }
}
