package com.casegen.modeling.service;

import com.casegen.collaboration.RealtimeEventPublisher;
import com.casegen.collaboration.RealtimeEventType;
import com.casegen.modeling.command.CreateUmlAssociationCommand;
import com.casegen.modeling.command.CreateUmlGeneralizationCommand;
import com.casegen.modeling.command.CreateUmlRealizationCommand;
import com.casegen.modeling.command.UmlAssociationEndCommand;
import com.casegen.modeling.command.UpdateUmlAssociationCommand;
import com.casegen.modeling.domain.AggregationKind;
import com.casegen.modeling.domain.AssociationEndOwnership;
import com.casegen.modeling.domain.ClassifierKind;
import com.casegen.modeling.domain.UmlAssociation;
import com.casegen.modeling.domain.UmlAssociationEnd;
import com.casegen.modeling.domain.UmlClassifier;
import com.casegen.modeling.domain.UmlElement;
import com.casegen.modeling.domain.UmlElementType;
import com.casegen.modeling.domain.UmlGeneralization;
import com.casegen.modeling.domain.UmlModel;
import com.casegen.modeling.domain.UmlModelStatus;
import com.casegen.modeling.domain.UmlPackage;
import com.casegen.modeling.domain.UmlProperty;
import com.casegen.modeling.domain.UmlRealization;
import com.casegen.modeling.domain.Visibility;
import com.casegen.modeling.dto.UmlAssociationEndResponse;
import com.casegen.modeling.dto.UmlAssociationResponse;
import com.casegen.modeling.dto.UmlGeneralizationResponse;
import com.casegen.modeling.dto.UmlRealizationResponse;
import com.casegen.modeling.exception.SemanticErrorCode;
import com.casegen.modeling.exception.SemanticException;
import com.casegen.modeling.repository.UmlAssociationEndRepository;
import com.casegen.modeling.repository.UmlAssociationRepository;
import com.casegen.modeling.repository.UmlClassifierRepository;
import com.casegen.modeling.repository.UmlElementRepository;
import com.casegen.modeling.repository.UmlGeneralizationRepository;
import com.casegen.modeling.repository.UmlModelRepository;
import com.casegen.modeling.repository.UmlPackageRepository;
import com.casegen.modeling.repository.UmlPropertyRepository;
import com.casegen.modeling.repository.UmlRealizationRepository;
import com.casegen.project.service.ProjectAuthorizationService;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UmlRelationshipService {

    private final UmlModelRepository modelRepository;
    private final UmlElementRepository elementRepository;
    private final UmlPackageRepository packageRepository;
    private final UmlClassifierRepository classifierRepository;
    private final UmlPropertyRepository propertyRepository;
    private final UmlAssociationRepository associationRepository;
    private final UmlAssociationEndRepository associationEndRepository;
    private final UmlGeneralizationRepository generalizationRepository;
    private final UmlRealizationRepository realizationRepository;
    private final ProjectAuthorizationService authorizationService;
    private final DiagramRepresentationCleanupService diagramCleanupService;
    private final RealtimeEventPublisher realtimeEvents;

    public UmlRelationshipService(
            UmlModelRepository modelRepository,
            UmlElementRepository elementRepository,
            UmlPackageRepository packageRepository,
            UmlClassifierRepository classifierRepository,
            UmlPropertyRepository propertyRepository,
            UmlAssociationRepository associationRepository,
            UmlAssociationEndRepository associationEndRepository,
            UmlGeneralizationRepository generalizationRepository,
            UmlRealizationRepository realizationRepository,
            ProjectAuthorizationService authorizationService,
            DiagramRepresentationCleanupService diagramCleanupService,
            RealtimeEventPublisher realtimeEvents) {
        this.modelRepository = modelRepository;
        this.elementRepository = elementRepository;
        this.packageRepository = packageRepository;
        this.classifierRepository = classifierRepository;
        this.propertyRepository = propertyRepository;
        this.associationRepository = associationRepository;
        this.associationEndRepository = associationEndRepository;
        this.generalizationRepository = generalizationRepository;
        this.realizationRepository = realizationRepository;
        this.authorizationService = authorizationService;
        this.diagramCleanupService = diagramCleanupService;
        this.realtimeEvents = realtimeEvents;
    }

    @Transactional
    public UmlAssociationResponse createAssociation(UUID modelId, CreateUmlAssociationCommand command) {
        UmlModel model = findEditableModel(modelId);
        authorizationService.requireWrite(model.getProject().getId());
        validateAssociationCommand(command.firstEnd(), command.secondEnd());
        UmlPackage umlPackage = resolvePackage(command.packageId(), modelId);
        ResolvedAssociationEnds resolved = resolveAssociationEnds(
                command.firstEnd(), command.secondEnd(), modelId);

        UmlElement associationElement = elementRepository.save(new UmlElement(
                model, UmlElementType.ASSOCIATION, normalizeOptional(command.name()), command.documentation()));
        UmlAssociation association = associationRepository.save(
                new UmlAssociation(associationElement, umlPackage));
        createEnd(model, association, command.firstEnd(), resolved.first(), resolved.second(), (short) 1);
        createEnd(model, association, command.secondEnd(), resolved.second(), resolved.first(), (short) 2);
        model.recordSemanticMutation();
        modelRepository.saveAndFlush(model);
        realtimeEvents.semantic(RealtimeEventType.RELATIONSHIP_CREATED, model.getProject().getId(),
                modelId, association.getElementId(), model.getRevision());
        return toAssociationResponse(association, model.getRevision());
    }

    @Transactional(readOnly = true)
    public List<UmlAssociationResponse> listAssociations(UUID modelId) {
        UmlModel model = findModel(modelId);
        authorizationService.requireRead(model.getProject().getId());
        return associationRepository.findByElementModelIdOrderByElementCreatedAtAsc(modelId).stream()
                .map(association -> toAssociationResponse(association, model.getRevision()))
                .toList();
    }

    @Transactional
    public UmlAssociationResponse updateAssociation(
            UUID modelId, UUID associationId, UpdateUmlAssociationCommand command) {
        UmlModel model = findEditableModel(modelId);
        authorizationService.requireWrite(model.getProject().getId());
        validateAssociationCommand(command.firstEnd(), command.secondEnd());
        UmlAssociation association = resolveAssociation(associationId, modelId);
        UmlPackage umlPackage = resolvePackage(command.packageId(), modelId);
        ResolvedAssociationEnds resolved = resolveAssociationEnds(
                command.firstEnd(), command.secondEnd(), modelId);
        List<UmlAssociationEnd> ends = associationEndRepository
                .findByAssociationElementIdOrderByMemberOrderAsc(associationId);
        if (ends.size() != 2) {
            throw error(SemanticErrorCode.INVALID_ASSOCIATION_END, HttpStatus.CONFLICT,
                    "An association must contain exactly two ends");
        }

        association.getElement().update(normalizeOptional(command.name()), command.documentation());
        association.update(umlPackage);
        updateEnd(ends.get(0), command.firstEnd(), resolved.first(), resolved.second());
        updateEnd(ends.get(1), command.secondEnd(), resolved.second(), resolved.first());
        model.recordSemanticMutation();
        modelRepository.saveAndFlush(model);
        realtimeEvents.semantic(RealtimeEventType.RELATIONSHIP_UPDATED, model.getProject().getId(),
                modelId, associationId, model.getRevision());
        return toAssociationResponse(association, model.getRevision());
    }

    @Transactional
    public void deleteAssociation(UUID modelId, UUID associationId) {
        UmlModel model = findEditableModel(modelId);
        authorizationService.requireWrite(model.getProject().getId());
        UmlAssociation association = resolveAssociation(associationId, modelId);
        diagramCleanupService.removeRelationshipRepresentations(associationId);
        List<UmlAssociationEnd> ends = associationEndRepository
                .findByAssociationElementIdOrderByMemberOrderAsc(associationId);
        List<UmlProperty> properties = ends.stream().map(UmlAssociationEnd::getProperty).toList();
        List<UmlElement> propertyElements = properties.stream().map(UmlProperty::getElement).toList();

        associationEndRepository.deleteAll(ends);
        associationEndRepository.flush();
        propertyRepository.deleteAll(properties);
        propertyRepository.flush();
        elementRepository.deleteAll(propertyElements);
        UmlElement associationElement = association.getElement();
        associationRepository.delete(association);
        associationRepository.flush();
        elementRepository.delete(associationElement);
        model.recordSemanticMutation();
        modelRepository.saveAndFlush(model);
        realtimeEvents.semantic(RealtimeEventType.RELATIONSHIP_DELETED, model.getProject().getId(),
                modelId, associationId, model.getRevision());
    }

    @Transactional
    public UmlGeneralizationResponse createGeneralization(
            UUID modelId, CreateUmlGeneralizationCommand command) {
        UmlModel model = findEditableModel(modelId);
        authorizationService.requireWrite(model.getProject().getId());
        UmlClassifier specific = resolveClassifier(command.specificClassifierId(), modelId);
        UmlClassifier general = resolveClassifier(command.generalClassifierId(), modelId);
        if (specific.getElementId().equals(general.getElementId())) {
            throw error(SemanticErrorCode.GENERALIZATION_CYCLE, HttpStatus.CONFLICT,
                    "A classifier cannot generalize itself");
        }
        if (specific.getKind() != general.getKind()) {
            throw error(SemanticErrorCode.GENERALIZATION_KIND_MISMATCH, HttpStatus.CONFLICT,
                    "Generalization requires CLASS to CLASS or INTERFACE to INTERFACE");
        }
        if (generalizationRepository.existsBySpecificClassifierElementIdAndGeneralClassifierElementId(
                specific.getElementId(), general.getElementId())) {
            throw error(SemanticErrorCode.DUPLICATE_GENERALIZATION, HttpStatus.CONFLICT,
                    "This generalization already exists");
        }
        ensureGeneralizationIsAcyclic(modelId, specific.getElementId(), general.getElementId());

        UmlElement element = elementRepository.save(new UmlElement(
                model, UmlElementType.GENERALIZATION, null, command.documentation()));
        UmlGeneralization generalization = generalizationRepository.save(new UmlGeneralization(
                element, specific, general, command.substitutable()));
        model.recordSemanticMutation();
        modelRepository.saveAndFlush(model);
        realtimeEvents.semantic(RealtimeEventType.RELATIONSHIP_CREATED, model.getProject().getId(),
                modelId, generalization.getElementId(), model.getRevision());
        return toGeneralizationResponse(generalization, model.getRevision());
    }

    @Transactional(readOnly = true)
    public List<UmlGeneralizationResponse> listGeneralizations(UUID modelId) {
        UmlModel model = findModel(modelId);
        authorizationService.requireRead(model.getProject().getId());
        return generalizationRepository.findByElementModelIdOrderByElementCreatedAtAsc(modelId).stream()
                .map(generalization -> toGeneralizationResponse(generalization, model.getRevision()))
                .toList();
    }

    @Transactional
    public void deleteGeneralization(UUID modelId, UUID generalizationId) {
        UmlModel model = findEditableModel(modelId);
        authorizationService.requireWrite(model.getProject().getId());
        UmlGeneralization generalization = resolveGeneralization(generalizationId, modelId);
        diagramCleanupService.removeRelationshipRepresentations(generalizationId);
        UmlElement element = generalization.getElement();
        generalizationRepository.delete(generalization);
        generalizationRepository.flush();
        elementRepository.delete(element);
        model.recordSemanticMutation();
        modelRepository.saveAndFlush(model);
        realtimeEvents.semantic(RealtimeEventType.RELATIONSHIP_DELETED, model.getProject().getId(),
                modelId, generalizationId, model.getRevision());
    }

    @Transactional
    public UmlRealizationResponse createRealization(UUID modelId, CreateUmlRealizationCommand command) {
        UmlModel model = findEditableModel(modelId);
        authorizationService.requireWrite(model.getProject().getId());
        UmlClassifier client = resolveClassifier(command.clientClassifierId(), modelId);
        UmlClassifier supplier = resolveClassifier(command.supplierClassifierId(), modelId);
        if (client.getElementId().equals(supplier.getElementId())) {
            throw error(SemanticErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST,
                    "Realization client and supplier must be distinct");
        }
        if (client.getKind() != ClassifierKind.CLASS) {
            throw error(SemanticErrorCode.REALIZATION_CLIENT_NOT_CLASS, HttpStatus.CONFLICT,
                    "Realization client must be a CLASS");
        }
        if (supplier.getKind() != ClassifierKind.INTERFACE) {
            throw error(SemanticErrorCode.REALIZATION_SUPPLIER_NOT_INTERFACE, HttpStatus.CONFLICT,
                    "Realization supplier must be an INTERFACE");
        }
        if (realizationRepository.existsByClientClassifierElementIdAndSupplierClassifierElementId(
                client.getElementId(), supplier.getElementId())) {
            throw error(SemanticErrorCode.DUPLICATE_REALIZATION, HttpStatus.CONFLICT,
                    "This realization already exists");
        }

        UmlElement element = elementRepository.save(new UmlElement(
                model, UmlElementType.REALIZATION, null, command.documentation()));
        UmlRealization realization = realizationRepository.save(
                new UmlRealization(element, client, supplier));
        model.recordSemanticMutation();
        modelRepository.saveAndFlush(model);
        realtimeEvents.semantic(RealtimeEventType.RELATIONSHIP_CREATED, model.getProject().getId(),
                modelId, realization.getElementId(), model.getRevision());
        return toRealizationResponse(realization, model.getRevision());
    }

    @Transactional(readOnly = true)
    public List<UmlRealizationResponse> listRealizations(UUID modelId) {
        UmlModel model = findModel(modelId);
        authorizationService.requireRead(model.getProject().getId());
        return realizationRepository.findByElementModelIdOrderByElementCreatedAtAsc(modelId).stream()
                .map(realization -> toRealizationResponse(realization, model.getRevision()))
                .toList();
    }

    @Transactional
    public void deleteRealization(UUID modelId, UUID realizationId) {
        UmlModel model = findEditableModel(modelId);
        authorizationService.requireWrite(model.getProject().getId());
        UmlRealization realization = resolveRealization(realizationId, modelId);
        diagramCleanupService.removeRelationshipRepresentations(realizationId);
        UmlElement element = realization.getElement();
        realizationRepository.delete(realization);
        realizationRepository.flush();
        elementRepository.delete(element);
        model.recordSemanticMutation();
        modelRepository.saveAndFlush(model);
        realtimeEvents.semantic(RealtimeEventType.RELATIONSHIP_DELETED, model.getProject().getId(),
                modelId, realizationId, model.getRevision());
    }

    private void createEnd(UmlModel model, UmlAssociation association, UmlAssociationEndCommand command,
            UmlClassifier typeClassifier, UmlClassifier oppositeClassifier, short memberOrder) {
        int lowerBound = resolveMultiplicity(command.lowerBound(), command.upperBound());
        AssociationEndOwnership ownership = defaultOwnership(command.ownership());
        UmlClassifier owner = ownership == AssociationEndOwnership.CLASSIFIER ? oppositeClassifier : null;
        UmlElement element = elementRepository.save(new UmlElement(
                model, UmlElementType.PROPERTY, normalizeOptional(command.roleName()), null));
        UmlProperty property = propertyRepository.save(UmlProperty.associationEnd(
                element, owner, defaultVisibility(command.visibility()), typeClassifier,
                lowerBound, command.upperBound()));
        associationEndRepository.save(new UmlAssociationEnd(
                property, association, command.navigable(), defaultAggregation(command.aggregation()),
                ownership, memberOrder));
    }

    private void updateEnd(UmlAssociationEnd end, UmlAssociationEndCommand command,
            UmlClassifier typeClassifier, UmlClassifier oppositeClassifier) {
        int lowerBound = resolveMultiplicity(command.lowerBound(), command.upperBound());
        AssociationEndOwnership ownership = defaultOwnership(command.ownership());
        UmlClassifier owner = ownership == AssociationEndOwnership.CLASSIFIER ? oppositeClassifier : null;
        end.getProperty().getElement().update(normalizeOptional(command.roleName()), null);
        end.getProperty().updateAssociationEnd(owner, defaultVisibility(command.visibility()),
                typeClassifier, lowerBound, command.upperBound());
        end.update(command.navigable(), defaultAggregation(command.aggregation()), ownership);
    }

    private void validateAssociationCommand(
            UmlAssociationEndCommand firstEnd, UmlAssociationEndCommand secondEnd) {
        if (firstEnd == null || secondEnd == null) {
            throw error(SemanticErrorCode.INVALID_ASSOCIATION_END, HttpStatus.BAD_REQUEST,
                    "An association requires exactly two ends");
        }
        if (firstEnd.classifierId() == null || secondEnd.classifierId() == null) {
            throw error(SemanticErrorCode.INVALID_ASSOCIATION_END, HttpStatus.BAD_REQUEST,
                    "Each association end requires a classifier");
        }
        resolveMultiplicity(firstEnd.lowerBound(), firstEnd.upperBound());
        resolveMultiplicity(secondEnd.lowerBound(), secondEnd.upperBound());
        validateCompositeWhole(firstEnd);
        validateCompositeWhole(secondEnd);
    }

    private void validateCompositeWhole(UmlAssociationEndCommand end) {
        if (defaultAggregation(end.aggregation()) == AggregationKind.COMPOSITE
                && (end.upperBound() == null || end.upperBound() > 1)) {
            throw error(SemanticErrorCode.INVALID_ASSOCIATION_END, HttpStatus.BAD_REQUEST,
                    "A composite whole must have an upper bound of at most one");
        }
    }

    private ResolvedAssociationEnds resolveAssociationEnds(
            UmlAssociationEndCommand firstEnd, UmlAssociationEndCommand secondEnd, UUID modelId) {
        return new ResolvedAssociationEnds(
                resolveClassifier(firstEnd.classifierId(), modelId),
                resolveClassifier(secondEnd.classifierId(), modelId));
    }

    private void ensureGeneralizationIsAcyclic(UUID modelId, UUID specificId, UUID generalId) {
        Map<UUID, List<UUID>> outgoing = new HashMap<>();
        for (UmlGeneralization relation
                : generalizationRepository.findByElementModelIdOrderByElementCreatedAtAsc(modelId)) {
            outgoing.computeIfAbsent(relation.getSpecificClassifier().getElementId(), ignored -> new java.util.ArrayList<>())
                    .add(relation.getGeneralClassifier().getElementId());
        }
        ArrayDeque<UUID> pending = new ArrayDeque<>();
        Set<UUID> visited = new HashSet<>();
        pending.add(generalId);
        while (!pending.isEmpty()) {
            UUID current = pending.removeFirst();
            if (current.equals(specificId)) {
                throw error(SemanticErrorCode.GENERALIZATION_CYCLE, HttpStatus.CONFLICT,
                        "Generalization would create an inheritance cycle");
            }
            if (visited.add(current)) {
                pending.addAll(outgoing.getOrDefault(current, List.of()));
            }
        }
    }

    private UmlModel findModel(UUID modelId) {
        return modelRepository.findById(modelId)
                .orElseThrow(() -> error(SemanticErrorCode.UML_MODEL_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "UML model does not exist"));
    }

    private UmlModel findEditableModel(UUID modelId) {
        UmlModel model = modelRepository.findByIdForUpdate(modelId)
                .orElseThrow(() -> error(SemanticErrorCode.UML_MODEL_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "UML model does not exist"));
        if (model.getStatus() != UmlModelStatus.EDITABLE) {
            throw error(SemanticErrorCode.MODEL_NOT_EDITABLE, HttpStatus.CONFLICT,
                    "UML model is not editable");
        }
        return model;
    }

    private UmlPackage resolvePackage(UUID packageId, UUID modelId) {
        if (packageId == null) {
            return null;
        }
        UmlPackage umlPackage = packageRepository.findById(packageId)
                .orElseThrow(() -> error(SemanticErrorCode.PACKAGE_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Package does not exist"));
        if (!umlPackage.getElement().getModel().getId().equals(modelId)) {
            throw error(SemanticErrorCode.RELATIONSHIP_MODEL_MISMATCH, HttpStatus.CONFLICT,
                    "Package belongs to a different UML model");
        }
        return umlPackage;
    }

    private UmlClassifier resolveClassifier(UUID classifierId, UUID modelId) {
        UmlClassifier classifier = classifierRepository.findById(classifierId)
                .orElseThrow(() -> error(SemanticErrorCode.CLASSIFIER_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Classifier does not exist"));
        if (!classifier.getElement().getModel().getId().equals(modelId)) {
            throw error(SemanticErrorCode.RELATIONSHIP_MODEL_MISMATCH, HttpStatus.CONFLICT,
                    "Relationship classifiers must belong to the same UML model");
        }
        return classifier;
    }

    private UmlAssociation resolveAssociation(UUID associationId, UUID modelId) {
        UmlAssociation association = associationRepository.findById(associationId)
                .orElseThrow(() -> error(SemanticErrorCode.ASSOCIATION_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Association does not exist"));
        ensureRelationshipModel(association.getElement(), modelId);
        return association;
    }

    private UmlGeneralization resolveGeneralization(UUID id, UUID modelId) {
        UmlGeneralization relation = generalizationRepository.findById(id)
                .orElseThrow(() -> error(SemanticErrorCode.GENERALIZATION_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Generalization does not exist"));
        ensureRelationshipModel(relation.getElement(), modelId);
        return relation;
    }

    private UmlRealization resolveRealization(UUID id, UUID modelId) {
        UmlRealization relation = realizationRepository.findById(id)
                .orElseThrow(() -> error(SemanticErrorCode.REALIZATION_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Realization does not exist"));
        ensureRelationshipModel(relation.getElement(), modelId);
        return relation;
    }

    private void ensureRelationshipModel(UmlElement element, UUID modelId) {
        if (!element.getModel().getId().equals(modelId)) {
            throw error(SemanticErrorCode.RELATIONSHIP_MODEL_MISMATCH, HttpStatus.CONFLICT,
                    "Relationship belongs to a different UML model");
        }
    }

    private int resolveMultiplicity(Integer lowerBound, Integer upperBound) {
        int resolvedLower = lowerBound == null ? 1 : lowerBound;
        if (resolvedLower < 0 || (upperBound != null && upperBound < resolvedLower)) {
            throw error(SemanticErrorCode.INVALID_MULTIPLICITY, HttpStatus.BAD_REQUEST,
                    "Multiplicity requires lower >= 0 and unlimited or upper >= lower");
        }
        return resolvedLower;
    }

    private UmlAssociationResponse toAssociationResponse(UmlAssociation association, long revision) {
        List<UmlAssociationEndResponse> ends = associationEndRepository
                .findByAssociationElementIdOrderByMemberOrderAsc(association.getElementId()).stream()
                .map(this::toAssociationEndResponse)
                .toList();
        UmlElement element = association.getElement();
        return new UmlAssociationResponse(
                element.getId(), element.getModel().getId(), element.getName(), element.getDocumentation(),
                association.getUmlPackage() == null ? null : association.getUmlPackage().getElementId(),
                ends, revision, element.getCreatedAt(), element.getUpdatedAt());
    }

    private UmlAssociationEndResponse toAssociationEndResponse(UmlAssociationEnd end) {
        UmlProperty property = end.getProperty();
        return new UmlAssociationEndResponse(
                property.getElementId(), property.getElement().getName(),
                property.getTypeClassifier().getElementId(),
                property.getOwnerClassifier() == null ? null : property.getOwnerClassifier().getElementId(),
                property.getVisibility(), property.getLowerBound(), property.getUpperBound(),
                end.isNavigable(), end.getAggregation(), end.getOwnership(), end.getMemberOrder());
    }

    private UmlGeneralizationResponse toGeneralizationResponse(
            UmlGeneralization relation, long revision) {
        UmlElement element = relation.getElement();
        return new UmlGeneralizationResponse(
                element.getId(), element.getModel().getId(),
                relation.getSpecificClassifier().getElementId(),
                relation.getGeneralClassifier().getElementId(), relation.isSubstitutable(),
                element.getDocumentation(), revision, element.getCreatedAt(), element.getUpdatedAt());
    }

    private UmlRealizationResponse toRealizationResponse(UmlRealization relation, long revision) {
        UmlElement element = relation.getElement();
        return new UmlRealizationResponse(
                element.getId(), element.getModel().getId(),
                relation.getClientClassifier().getElementId(),
                relation.getSupplierClassifier().getElementId(), element.getDocumentation(),
                revision, element.getCreatedAt(), element.getUpdatedAt());
    }

    private Visibility defaultVisibility(Visibility visibility) {
        return visibility == null ? Visibility.PUBLIC : visibility;
    }

    private AggregationKind defaultAggregation(AggregationKind aggregation) {
        return aggregation == null ? AggregationKind.NONE : aggregation;
    }

    private AssociationEndOwnership defaultOwnership(AssociationEndOwnership ownership) {
        return ownership == null ? AssociationEndOwnership.ASSOCIATION : ownership;
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private SemanticException error(SemanticErrorCode code, HttpStatus status, String message) {
        return new SemanticException(code, status, message);
    }

    private record ResolvedAssociationEnds(UmlClassifier first, UmlClassifier second) {
    }
}
