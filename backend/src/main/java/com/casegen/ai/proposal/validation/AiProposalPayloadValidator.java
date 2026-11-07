package com.casegen.ai.proposal.validation;

import com.casegen.ai.proposal.context.AiProposalContext;
import com.casegen.ai.proposal.domain.AiProposalType;
import com.casegen.ai.proposal.payload.AddAssociationPayload;
import com.casegen.ai.proposal.payload.AddAttributePayload;
import com.casegen.ai.proposal.payload.AddClassifierPayload;
import com.casegen.ai.proposal.payload.AddGeneralizationPayload;
import com.casegen.ai.proposal.payload.AddOperationPayload;
import com.casegen.ai.proposal.payload.AddRealizationPayload;
import com.casegen.ai.proposal.payload.AiProposalPayload;
import com.casegen.ai.proposal.payload.AssociationEndProposalPayload;
import com.casegen.modeling.domain.AggregationKind;
import com.casegen.modeling.domain.AssociationEndOwnership;
import com.casegen.modeling.domain.ClassifierKind;
import com.casegen.modeling.domain.TypeKind;
import com.casegen.modeling.domain.UmlElementType;
import com.casegen.modeling.domain.Visibility;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AiProposalPayloadValidator {

    public AiProposalPayload validate(AiProposalType type, AiProposalPayload payload,
            AiProposalContext context) {
        if (type == null || payload == null || context == null) {
            throw error(AiProposalValidationErrorCode.INVALID_PAYLOAD,
                    "Proposal type, payload and context are required");
        }
        return switch (type) {
            case ADD_CLASSIFIER -> validateClassifier(require(payload, AddClassifierPayload.class), context);
            case ADD_ATTRIBUTE -> validateAttribute(require(payload, AddAttributePayload.class), context);
            case ADD_OPERATION -> validateOperation(require(payload, AddOperationPayload.class), context);
            case ADD_ASSOCIATION -> validateAssociation(require(payload, AddAssociationPayload.class), context);
            case ADD_GENERALIZATION -> validateGeneralization(
                    require(payload, AddGeneralizationPayload.class), context);
            case ADD_REALIZATION -> validateRealization(require(payload, AddRealizationPayload.class), context);
            case UPDATE_CLASSIFIER, UPDATE_ATTRIBUTE -> throw error(
                    AiProposalValidationErrorCode.INVALID_PAYLOAD,
                    "UPDATE proposals require review payload schema 2 validation");
        };
    }

    private AddClassifierPayload validateClassifier(AddClassifierPayload value, AiProposalContext context) {
        String name = name(value.name());
        require(value.kind() != null, AiProposalValidationErrorCode.INVALID_PAYLOAD,
                "Classifier kind is required");
        require(value.abstractClassifier() != null && value.active() != null,
                AiProposalValidationErrorCode.INVALID_PAYLOAD,
                "Classifier flags are required");
        if (value.kind() == ClassifierKind.INTERFACE && value.active()) {
            throw error(AiProposalValidationErrorCode.INCOMPATIBLE_CLASSIFIER_KIND,
                    "An INTERFACE cannot be active");
        }
        requirePackage(value.packageId(), context);
        boolean duplicate = context.classifiers().stream()
                .filter(entry -> java.util.Objects.equals(entry.packageId(), value.packageId()))
                .anyMatch(entry -> entry.name().equalsIgnoreCase(name));
        require(!duplicate, AiProposalValidationErrorCode.DUPLICATE_ELEMENT,
                "A classifier with this name already exists in the package");
        return new AddClassifierPayload(value.kind(), name, value.packageId(),
                value.visibility() == null ? Visibility.PUBLIC : value.visibility(),
                value.abstractClassifier(), value.active(), normalizeText(value.documentation()));
    }

    private AddAttributePayload validateAttribute(AddAttributePayload value, AiProposalContext context) {
        requireClassifier(value.ownerClassifierId(), context);
        String name = name(value.name());
        require(value.typeKind() != null, AiProposalValidationErrorCode.INVALID_PAYLOAD,
                "Attribute type kind is required");
        if (value.typeKind() == TypeKind.PRIMITIVE) {
            require(value.primitiveType() != null && value.typeClassifierId() == null,
                    AiProposalValidationErrorCode.INVALID_PAYLOAD,
                    "A primitive attribute requires only primitiveType");
        } else {
            require(value.primitiveType() == null && value.typeClassifierId() != null,
                    AiProposalValidationErrorCode.INVALID_PAYLOAD,
                    "A classifier attribute requires only typeClassifierId");
            requireClassifier(value.typeClassifierId(), context);
        }
        int lower = multiplicity(value.lowerBound(), value.upperBound());
        require(value.staticProperty() != null && value.readOnly() != null && value.derived() != null,
                AiProposalValidationErrorCode.INVALID_PAYLOAD, "Attribute flags are required");
        return new AddAttributePayload(value.ownerClassifierId(), name,
                value.visibility() == null ? Visibility.PRIVATE : value.visibility(), value.typeKind(),
                value.primitiveType(), value.typeClassifierId(), lower, value.upperBound(),
                value.staticProperty(), value.readOnly(), value.derived());
    }

    private AddOperationPayload validateOperation(AddOperationPayload value, AiProposalContext context) {
        requireClassifier(value.ownerClassifierId(), context);
        require(value.abstractOperation() != null && value.staticOperation() != null && value.query() != null,
                AiProposalValidationErrorCode.INVALID_PAYLOAD, "Operation flags are required");
        return new AddOperationPayload(value.ownerClassifierId(), name(value.name()),
                value.visibility() == null ? Visibility.PUBLIC : value.visibility(),
                value.abstractOperation(), value.staticOperation(), value.query());
    }

    private AddGeneralizationPayload validateGeneralization(AddGeneralizationPayload value,
            AiProposalContext context) {
        var specific = requireClassifier(value.specificClassifierId(), context);
        var general = requireClassifier(value.generalClassifierId(), context);
        require(!specific.id().equals(general.id()), AiProposalValidationErrorCode.GENERALIZATION_CYCLE,
                "A classifier cannot generalize itself");
        require(specific.kind() == general.kind(),
                AiProposalValidationErrorCode.INCOMPATIBLE_CLASSIFIER_KIND,
                "Generalization requires CLASS to CLASS or INTERFACE to INTERFACE");
        require(!relationshipExists(context, UmlElementType.GENERALIZATION, specific.id(), general.id()),
                AiProposalValidationErrorCode.DUPLICATE_ELEMENT, "This generalization already exists");
        require(!createsGeneralizationCycle(context, specific.id(), general.id()),
                AiProposalValidationErrorCode.GENERALIZATION_CYCLE,
                "The proposed generalization would create a cycle");
        require(value.substitutable() != null, AiProposalValidationErrorCode.INVALID_PAYLOAD,
                "substitutable is required");
        return new AddGeneralizationPayload(specific.id(), general.id(), value.substitutable(),
                normalizeText(value.documentation()));
    }

    private AddRealizationPayload validateRealization(AddRealizationPayload value,
            AiProposalContext context) {
        var client = requireClassifier(value.clientClassifierId(), context);
        var supplier = requireClassifier(value.supplierClassifierId(), context);
        require(!client.id().equals(supplier.id()), AiProposalValidationErrorCode.INVALID_RELATIONSHIP,
                "Realization client and supplier must be distinct");
        require(client.kind() == ClassifierKind.CLASS,
                AiProposalValidationErrorCode.INCOMPATIBLE_CLASSIFIER_KIND,
                "Realization client must be a CLASS");
        require(supplier.kind() == ClassifierKind.INTERFACE,
                AiProposalValidationErrorCode.INCOMPATIBLE_CLASSIFIER_KIND,
                "Realization supplier must be an INTERFACE");
        require(!relationshipExists(context, UmlElementType.REALIZATION, client.id(), supplier.id()),
                AiProposalValidationErrorCode.DUPLICATE_ELEMENT, "This realization already exists");
        return new AddRealizationPayload(client.id(), supplier.id(), normalizeText(value.documentation()));
    }

    private AddAssociationPayload validateAssociation(AddAssociationPayload value,
            AiProposalContext context) {
        require(value.firstEnd() != null && value.secondEnd() != null,
                AiProposalValidationErrorCode.INVALID_RELATIONSHIP,
                "An association requires exactly two ends");
        requirePackage(value.packageId(), context);
        AssociationEndProposalPayload first = validateEnd(value.firstEnd(), context);
        AssociationEndProposalPayload second = validateEnd(value.secondEnd(), context);
        return new AddAssociationPayload(normalizeOptionalName(value.name()), value.packageId(), first, second);
    }

    private AssociationEndProposalPayload validateEnd(AssociationEndProposalPayload value,
            AiProposalContext context) {
        requireClassifier(value.classifierId(), context);
        int lower = multiplicity(value.lowerBound(), value.upperBound());
        AggregationKind aggregation = value.aggregation() == null ? AggregationKind.NONE : value.aggregation();
        if (aggregation == AggregationKind.COMPOSITE) {
            require(value.upperBound() != null && value.upperBound() <= 1,
                    AiProposalValidationErrorCode.INVALID_RELATIONSHIP,
                    "A composite whole must have an upper bound of at most one");
        }
        require(value.navigable() != null, AiProposalValidationErrorCode.INVALID_PAYLOAD,
                "Association navigable flag is required");
        return new AssociationEndProposalPayload(value.classifierId(),
                normalizeOptionalName(value.roleName()), lower, value.upperBound(), value.navigable(),
                aggregation, value.ownership() == null
                        ? AssociationEndOwnership.ASSOCIATION : value.ownership());
    }

    private boolean createsGeneralizationCycle(AiProposalContext context, UUID specific, UUID general) {
        Map<UUID, List<UUID>> outgoing = new HashMap<>();
        context.relationships().stream()
                .filter(entry -> entry.type() == UmlElementType.GENERALIZATION)
                .forEach(entry -> outgoing.computeIfAbsent(entry.firstClassifierId(), ignored ->
                        new java.util.ArrayList<>()).add(entry.secondClassifierId()));
        ArrayDeque<UUID> pending = new ArrayDeque<>();
        Set<UUID> visited = new HashSet<>();
        pending.add(general);
        while (!pending.isEmpty()) {
            UUID current = pending.removeFirst();
            if (current.equals(specific)) {
                return true;
            }
            if (visited.add(current)) {
                pending.addAll(outgoing.getOrDefault(current, List.of()));
            }
        }
        return false;
    }

    private boolean relationshipExists(AiProposalContext context, UmlElementType type,
            UUID first, UUID second) {
        return context.relationships().stream().anyMatch(entry -> entry.type() == type
                && entry.firstClassifierId().equals(first) && entry.secondClassifierId().equals(second));
    }

    private AiProposalContext.ClassifierEntry requireClassifier(UUID id, AiProposalContext context) {
        if (id == null) {
            throw error(AiProposalValidationErrorCode.INVALID_PAYLOAD, "Classifier id is required");
        }
        return context.findClassifier(id).orElseThrow(() -> error(
                AiProposalValidationErrorCode.REFERENCED_ELEMENT_NOT_FOUND,
                "Referenced classifier does not exist in the UML model"));
    }

    private void requirePackage(UUID id, AiProposalContext context) {
        if (id != null && context.findPackage(id).isEmpty()) {
            throw error(AiProposalValidationErrorCode.REFERENCED_ELEMENT_NOT_FOUND,
                    "Referenced package does not exist in the UML model");
        }
    }

    private int multiplicity(Integer lower, Integer upper) {
        int resolved = lower == null ? 1 : lower;
        require(resolved >= 0 && (upper == null || upper >= resolved),
                AiProposalValidationErrorCode.INVALID_MULTIPLICITY,
                "Multiplicity requires lower >= 0 and unlimited or upper >= lower");
        return resolved;
    }

    private String name(String value) {
        require(value != null && !value.isBlank(), AiProposalValidationErrorCode.INVALID_PAYLOAD,
                "Name is required");
        String normalized = value.trim();
        require(normalized.length() <= 180, AiProposalValidationErrorCode.INVALID_PAYLOAD,
                "Name must not exceed 180 characters");
        return normalized;
    }

    private String normalizeOptionalName(String value) {
        return value == null || value.isBlank() ? null : name(value);
    }

    private String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private <T extends AiProposalPayload> T require(AiProposalPayload payload, Class<T> expected) {
        if (!expected.isInstance(payload)) {
            throw error(AiProposalValidationErrorCode.INVALID_PAYLOAD,
                    "Proposal type and payload are incompatible");
        }
        return expected.cast(payload);
    }

    private void require(boolean condition, AiProposalValidationErrorCode code, String message) {
        if (!condition) {
            throw error(code, message);
        }
    }

    private AiProposalValidationException error(AiProposalValidationErrorCode code, String message) {
        return new AiProposalValidationException(code, message);
    }
}
