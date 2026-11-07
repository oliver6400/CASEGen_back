package com.casegen.ai.proposal.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.casegen.ai.proposal.context.AiProposalContext;
import com.casegen.ai.proposal.domain.AiProposalType;
import com.casegen.ai.proposal.payload.AddAssociationPayload;
import com.casegen.ai.proposal.payload.AddAttributePayload;
import com.casegen.ai.proposal.payload.AddClassifierPayload;
import com.casegen.ai.proposal.payload.AddGeneralizationPayload;
import com.casegen.ai.proposal.payload.AddOperationPayload;
import com.casegen.ai.proposal.payload.AddRealizationPayload;
import com.casegen.ai.proposal.payload.AssociationEndProposalPayload;
import com.casegen.modeling.domain.AggregationKind;
import com.casegen.modeling.domain.AssociationEndOwnership;
import com.casegen.modeling.domain.ClassifierKind;
import com.casegen.modeling.domain.PrimitiveType;
import com.casegen.modeling.domain.TypeKind;
import com.casegen.modeling.domain.UmlElementType;
import com.casegen.modeling.domain.Visibility;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiProposalPayloadValidatorTest {
    private final AiProposalPayloadValidator validator = new AiProposalPayloadValidator();
    private UUID packageId;
    private UUID customer;
    private UUID person;
    private UUID entity;
    private UUID contract;
    private AiProposalContext context;

    @BeforeEach
    void setUp() {
        packageId = UUID.randomUUID();
        customer = UUID.randomUUID();
        person = UUID.randomUUID();
        entity = UUID.randomUUID();
        contract = UUID.randomUUID();
        context = context(List.of(
                classifier(customer, "Customer", ClassifierKind.CLASS, packageId),
                classifier(person, "Person", ClassifierKind.CLASS, packageId),
                classifier(entity, "Entity", ClassifierKind.CLASS, packageId),
                classifier(contract, "Contract", ClassifierKind.INTERFACE, packageId)), List.of());
    }

    @Test
    void validatesAndNormalizesClassifier() {
        var result = (AddClassifierPayload) validator.validate(AiProposalType.ADD_CLASSIFIER,
                new AddClassifierPayload(ClassifierKind.CLASS, "  Invoice  ", packageId,
                        null, false, true, " docs "), context);
        assertThat(result.name()).isEqualTo("Invoice");
        assertThat(result.visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(result.documentation()).isEqualTo("docs");
    }

    @Test
    void rejectsDuplicateClassifierInSamePackage() {
        rejects(AiProposalType.ADD_CLASSIFIER,
                new AddClassifierPayload(ClassifierKind.CLASS, "customer", packageId,
                        Visibility.PUBLIC, false, true, null),
                AiProposalValidationErrorCode.DUPLICATE_ELEMENT);
    }

    @Test
    void validatesAttributeAndDefaultsMultiplicity() {
        var result = (AddAttributePayload) validator.validate(AiProposalType.ADD_ATTRIBUTE,
                attribute(customer, TypeKind.PRIMITIVE, PrimitiveType.STRING, null, null, null), context);
        assertThat(result.lowerBound()).isEqualTo(1);
        assertThat(result.visibility()).isEqualTo(Visibility.PRIVATE);
    }

    @Test
    void rejectsMissingAttributeOwnerAndExternalTypeAndBadMultiplicity() {
        rejects(AiProposalType.ADD_ATTRIBUTE,
                attribute(UUID.randomUUID(), TypeKind.PRIMITIVE, PrimitiveType.STRING, null, 1, 1),
                AiProposalValidationErrorCode.REFERENCED_ELEMENT_NOT_FOUND);
        rejects(AiProposalType.ADD_ATTRIBUTE,
                attribute(customer, TypeKind.CLASSIFIER, null, UUID.randomUUID(), 1, 1),
                AiProposalValidationErrorCode.REFERENCED_ELEMENT_NOT_FOUND);
        rejects(AiProposalType.ADD_ATTRIBUTE,
                attribute(customer, TypeKind.PRIMITIVE, PrimitiveType.STRING, null, 2, 1),
                AiProposalValidationErrorCode.INVALID_MULTIPLICITY);
    }

    @Test
    void validatesOperationAndRejectsMissingOwner() {
        assertThat(validator.validate(AiProposalType.ADD_OPERATION, operation(customer), context))
                .isInstanceOf(AddOperationPayload.class);
        rejects(AiProposalType.ADD_OPERATION, operation(UUID.randomUUID()),
                AiProposalValidationErrorCode.REFERENCED_ELEMENT_NOT_FOUND);
    }

    @Test
    void validatesGeneralization() {
        assertThat(validator.validate(AiProposalType.ADD_GENERALIZATION,
                generalization(customer, person), context)).isInstanceOf(AddGeneralizationPayload.class);
    }

    @Test
    void rejectsSelfDuplicateCycleKindAndExternalGeneralizations() {
        rejects(AiProposalType.ADD_GENERALIZATION, generalization(customer, customer),
                AiProposalValidationErrorCode.GENERALIZATION_CYCLE);
        context = withRelationship(UmlElementType.GENERALIZATION, customer, person);
        rejects(AiProposalType.ADD_GENERALIZATION, generalization(customer, person),
                AiProposalValidationErrorCode.DUPLICATE_ELEMENT);
        context = context(context.classifiers(), List.of(
                relationship(UmlElementType.GENERALIZATION, person, entity),
                relationship(UmlElementType.GENERALIZATION, entity, customer)));
        rejects(AiProposalType.ADD_GENERALIZATION, generalization(customer, person),
                AiProposalValidationErrorCode.GENERALIZATION_CYCLE);
        rejects(AiProposalType.ADD_GENERALIZATION, generalization(customer, contract),
                AiProposalValidationErrorCode.INCOMPATIBLE_CLASSIFIER_KIND);
        rejects(AiProposalType.ADD_GENERALIZATION, generalization(customer, UUID.randomUUID()),
                AiProposalValidationErrorCode.REFERENCED_ELEMENT_NOT_FOUND);
    }

    @Test
    void validatesRealizationAndRejectsWrongSupplierAndDuplicate() {
        var payload = new AddRealizationPayload(customer, contract, null);
        assertThat(validator.validate(AiProposalType.ADD_REALIZATION, payload, context))
                .isInstanceOf(AddRealizationPayload.class);
        rejects(AiProposalType.ADD_REALIZATION, new AddRealizationPayload(customer, person, null),
                AiProposalValidationErrorCode.INCOMPATIBLE_CLASSIFIER_KIND);
        context = withRelationship(UmlElementType.REALIZATION, customer, contract);
        rejects(AiProposalType.ADD_REALIZATION, payload, AiProposalValidationErrorCode.DUPLICATE_ELEMENT);
    }

    @Test
    void validatesAssociationAndRejectsExternalAndInvalidMultiplicityAndComposite() {
        var valid = association(customer, person, AggregationKind.NONE, 1, 1);
        assertThat(validator.validate(AiProposalType.ADD_ASSOCIATION, valid, context))
                .isInstanceOf(AddAssociationPayload.class);
        rejects(AiProposalType.ADD_ASSOCIATION,
                association(customer, UUID.randomUUID(), AggregationKind.NONE, 1, 1),
                AiProposalValidationErrorCode.REFERENCED_ELEMENT_NOT_FOUND);
        rejects(AiProposalType.ADD_ASSOCIATION,
                association(customer, person, AggregationKind.NONE, 2, 1),
                AiProposalValidationErrorCode.INVALID_MULTIPLICITY);
        rejects(AiProposalType.ADD_ASSOCIATION,
                association(customer, person, AggregationKind.COMPOSITE, 0, null),
                AiProposalValidationErrorCode.INVALID_RELATIONSHIP);
    }

    private AddAttributePayload attribute(UUID owner, TypeKind kind, PrimitiveType primitive,
            UUID classifierType, Integer lower, Integer upper) {
        return new AddAttributePayload(owner, "code", null, kind, primitive, classifierType,
                lower, upper, false, false, false);
    }

    private AddOperationPayload operation(UUID owner) {
        return new AddOperationPayload(owner, "save", null, false, false, false);
    }

    private AddGeneralizationPayload generalization(UUID specific, UUID general) {
        return new AddGeneralizationPayload(specific, general, true, null);
    }

    private AddAssociationPayload association(UUID first, UUID second, AggregationKind aggregation,
            Integer lower, Integer upper) {
        return new AddAssociationPayload(null, packageId,
                new AssociationEndProposalPayload(first, null, lower, upper, true,
                        aggregation, AssociationEndOwnership.ASSOCIATION),
                new AssociationEndProposalPayload(second, null, 1, 1, true,
                        AggregationKind.NONE, AssociationEndOwnership.ASSOCIATION));
    }

    private AiProposalContext withRelationship(UmlElementType type, UUID first, UUID second) {
        return context(context.classifiers(), List.of(relationship(type, first, second)));
    }

    private AiProposalContext context(List<AiProposalContext.ClassifierEntry> classifiers,
            List<AiProposalContext.RelationshipEntry> relationships) {
        return new AiProposalContext(UUID.randomUUID(), UUID.randomUUID(), 7, null, null,
                List.of(new AiProposalContext.PackageEntry(packageId, "domain", null, true)),
                classifiers, new ArrayList<>(), new ArrayList<>(), relationships);
    }

    private AiProposalContext.ClassifierEntry classifier(UUID id, String name,
            ClassifierKind kind, UUID packageId) {
        return new AiProposalContext.ClassifierEntry(id, name, kind, packageId, true);
    }

    private AiProposalContext.RelationshipEntry relationship(UmlElementType type, UUID first, UUID second) {
        return new AiProposalContext.RelationshipEntry(UUID.randomUUID(), type, first, second, true);
    }

    private void rejects(AiProposalType type, com.casegen.ai.proposal.payload.AiProposalPayload payload,
            AiProposalValidationErrorCode code) {
        assertThatThrownBy(() -> validator.validate(type, payload, context))
                .isInstanceOf(AiProposalValidationException.class)
                .extracting(error -> ((AiProposalValidationException) error).getCode())
                .isEqualTo(code);
    }
}
