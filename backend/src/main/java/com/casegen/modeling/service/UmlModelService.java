package com.casegen.modeling.service;

import com.casegen.collaboration.RealtimeEventPublisher;
import com.casegen.collaboration.RealtimeEventType;
import com.casegen.modeling.command.CreateUmlClassifierCommand;
import com.casegen.modeling.command.CreateUmlModelCommand;
import com.casegen.modeling.command.CreateUmlOperationCommand;
import com.casegen.modeling.command.CreateUmlPackageCommand;
import com.casegen.modeling.command.CreateUmlParameterCommand;
import com.casegen.modeling.command.CreateUmlPropertyCommand;
import com.casegen.modeling.domain.ClassifierKind;
import com.casegen.modeling.domain.ParameterDirection;
import com.casegen.modeling.domain.UmlClassifier;
import com.casegen.modeling.domain.UmlElement;
import com.casegen.modeling.domain.UmlElementType;
import com.casegen.modeling.domain.UmlModel;
import com.casegen.modeling.domain.UmlModelStatus;
import com.casegen.modeling.domain.UmlOperation;
import com.casegen.modeling.domain.UmlPackage;
import com.casegen.modeling.domain.UmlParameter;
import com.casegen.modeling.domain.UmlProperty;
import com.casegen.modeling.domain.TypeKind;
import com.casegen.modeling.domain.Visibility;
import com.casegen.modeling.dto.UmlClassifierResponse;
import com.casegen.modeling.dto.UmlModelResponse;
import com.casegen.modeling.dto.UmlOperationResponse;
import com.casegen.modeling.dto.UmlPackageResponse;
import com.casegen.modeling.dto.UmlParameterResponse;
import com.casegen.modeling.dto.UmlPropertyResponse;
import com.casegen.modeling.exception.SemanticErrorCode;
import com.casegen.modeling.exception.SemanticException;
import com.casegen.modeling.repository.UmlClassifierRepository;
import com.casegen.modeling.repository.UmlElementRepository;
import com.casegen.modeling.repository.UmlModelRepository;
import com.casegen.modeling.repository.UmlOperationRepository;
import com.casegen.modeling.repository.UmlPackageRepository;
import com.casegen.modeling.repository.UmlParameterRepository;
import com.casegen.modeling.repository.UmlPropertyRepository;
import com.casegen.project.domain.Project;
import com.casegen.project.repository.ProjectRepository;
import com.casegen.project.service.ProjectAuthorizationService;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UmlModelService {

    private final ProjectRepository projectRepository;
    private final UmlModelRepository modelRepository;
    private final UmlElementRepository elementRepository;
    private final UmlPackageRepository packageRepository;
    private final UmlClassifierRepository classifierRepository;
    private final UmlPropertyRepository propertyRepository;
    private final UmlOperationRepository operationRepository;
    private final UmlParameterRepository parameterRepository;
    private final ProjectAuthorizationService authorizationService;
    private final DiagramRepresentationCleanupService diagramCleanupService;
    private final RealtimeEventPublisher realtimeEvents;

    public UmlModelService(
            ProjectRepository projectRepository,
            UmlModelRepository modelRepository,
            UmlElementRepository elementRepository,
            UmlPackageRepository packageRepository,
            UmlClassifierRepository classifierRepository,
            UmlPropertyRepository propertyRepository,
            UmlOperationRepository operationRepository,
            UmlParameterRepository parameterRepository,
            ProjectAuthorizationService authorizationService,
            DiagramRepresentationCleanupService diagramCleanupService,
            RealtimeEventPublisher realtimeEvents) {
        this.projectRepository = projectRepository;
        this.modelRepository = modelRepository;
        this.elementRepository = elementRepository;
        this.packageRepository = packageRepository;
        this.classifierRepository = classifierRepository;
        this.propertyRepository = propertyRepository;
        this.operationRepository = operationRepository;
        this.parameterRepository = parameterRepository;
        this.authorizationService = authorizationService;
        this.diagramCleanupService = diagramCleanupService;
        this.realtimeEvents = realtimeEvents;
    }

    @Transactional
    public UmlModelResponse createModel(UUID projectId, CreateUmlModelCommand command) {
        var creator = authorizationService.requireWrite(projectId);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> error(SemanticErrorCode.PROJECT_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Project does not exist"));
        String name = normalizeName(command.name());
        if (modelRepository.existsByProjectIdAndName(projectId, name)) {
            throw error(SemanticErrorCode.DUPLICATE_MODEL_NAME, HttpStatus.CONFLICT,
                    "A UML model with this name already exists in the project");
        }

        try {
            UmlModel model = modelRepository.saveAndFlush(
                    new UmlModel(project, name, command.documentation(), creator));
            return toModelResponse(model);
        } catch (DataIntegrityViolationException exception) {
            throw error(SemanticErrorCode.DUPLICATE_MODEL_NAME, HttpStatus.CONFLICT,
                    "A UML model with this name already exists in the project");
        }
    }

    @Transactional(readOnly = true)
    public List<UmlModelResponse> listModels(UUID projectId) {
        authorizationService.requireRead(projectId);
        if (!projectRepository.existsById(projectId)) {
            throw error(SemanticErrorCode.PROJECT_NOT_FOUND, HttpStatus.NOT_FOUND,
                    "Project does not exist");
        }
        return modelRepository.findByProjectIdOrderByNameAsc(projectId).stream()
                .map(this::toModelResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UmlModelResponse getModel(UUID modelId) {
        UmlModel model = findModel(modelId);
        authorizationService.requireRead(model.getProject().getId());
        return toModelResponse(model);
    }

    @Transactional
    public UmlPackageResponse createPackage(UUID modelId, CreateUmlPackageCommand command) {
        UmlModel model = findEditableModel(modelId);
        authorizationService.requireWrite(model.getProject().getId());
        UmlPackage parent = resolvePackage(command.parentPackageId(), modelId,
                SemanticErrorCode.PACKAGE_MODEL_MISMATCH);

        UmlElement element = elementRepository.save(new UmlElement(
                model, UmlElementType.PACKAGE, normalizeName(command.name()), command.documentation()));
        UmlPackage umlPackage = packageRepository.save(new UmlPackage(element, parent));
        model.recordSemanticMutation();
        modelRepository.saveAndFlush(model);
        realtimeEvents.semantic(RealtimeEventType.PACKAGE_CREATED, model.getProject().getId(),
                modelId, element.getId(), model.getRevision());
        return toPackageResponse(umlPackage, model.getRevision());
    }

    @Transactional(readOnly = true)
    public List<UmlPackageResponse> listPackages(UUID modelId) {
        UmlModel model = findModel(modelId);
        authorizationService.requireRead(model.getProject().getId());
        return packageRepository.findByElementModelIdOrderByElementNameAsc(modelId).stream()
                .map(umlPackage -> toPackageResponse(umlPackage, model.getRevision()))
                .toList();
    }

    @Transactional
    public UmlClassifierResponse createClassifier(UUID modelId, CreateUmlClassifierCommand command) {
        UmlModel model = findEditableModel(modelId);
        authorizationService.requireWrite(model.getProject().getId());
        if (command.kind() == null) {
            throw error(SemanticErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                    "Classifier kind is required");
        }
        Visibility visibility = command.visibility() == null ? Visibility.PUBLIC : command.visibility();
        if (command.kind() == ClassifierKind.INTERFACE && command.active()) {
            throw error(SemanticErrorCode.INTERFACE_CANNOT_BE_ACTIVE, HttpStatus.BAD_REQUEST,
                    "An INTERFACE cannot be active");
        }
        UmlPackage umlPackage = resolvePackage(command.packageId(), modelId,
                SemanticErrorCode.CLASSIFIER_PACKAGE_MODEL_MISMATCH);

        String name = normalizeName(command.name());
        ensureClassifierNameAvailable(modelId, umlPackage, name, null);
        UmlElement element = elementRepository.save(new UmlElement(
                model, UmlElementType.CLASSIFIER, name, command.documentation()));
        UmlClassifier classifier = classifierRepository.save(new UmlClassifier(
                element, umlPackage, command.kind(), visibility, command.abstractClassifier(), command.active()));
        model.recordSemanticMutation();
        modelRepository.saveAndFlush(model);
        realtimeEvents.semantic(RealtimeEventType.CLASSIFIER_CREATED, model.getProject().getId(),
                modelId, classifier.getElementId(), model.getRevision());
        return toClassifierResponse(classifier, model.getRevision());
    }

    @Transactional(readOnly = true)
    public List<UmlClassifierResponse> listClassifiers(UUID modelId) {
        UmlModel model = findModel(modelId);
        authorizationService.requireRead(model.getProject().getId());
        return classifierRepository.findByElementModelIdOrderByElementNameAsc(modelId).stream()
                .map(classifier -> toClassifierResponse(classifier, model.getRevision()))
                .toList();
    }

    @Transactional
    public UmlClassifierResponse updateClassifier(
            UUID modelId, UUID classifierId, CreateUmlClassifierCommand command) {
        UmlModel model = findEditableModel(modelId);
        authorizationService.requireWrite(model.getProject().getId());
        UmlClassifier classifier = resolveClassifier(classifierId, modelId,
                SemanticErrorCode.CLASSIFIER_MODEL_MISMATCH);

        if (command.kind() == null) {
            throw error(SemanticErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                    "Classifier kind is required");
        }
        if (command.kind() == ClassifierKind.INTERFACE && command.active()) {
            throw error(SemanticErrorCode.INTERFACE_CANNOT_BE_ACTIVE, HttpStatus.BAD_REQUEST,
                    "An INTERFACE cannot be active");
        }
        UmlPackage umlPackage = resolvePackage(command.packageId(), modelId,
                SemanticErrorCode.CLASSIFIER_PACKAGE_MODEL_MISMATCH);
        String name = normalizeName(command.name());
        ensureClassifierNameAvailable(modelId, umlPackage, name, classifierId);
        classifier.getElement().update(name, command.documentation());
        classifier.update(umlPackage, command.kind(),
                command.visibility() == null ? Visibility.PUBLIC : command.visibility(),
                command.abstractClassifier(), command.active());
        model.recordSemanticMutation();
        modelRepository.saveAndFlush(model);
        realtimeEvents.semantic(RealtimeEventType.CLASSIFIER_UPDATED, model.getProject().getId(),
                modelId, classifierId, model.getRevision());
        return toClassifierResponse(classifier, model.getRevision());
    }

    @Transactional
    public void deleteClassifier(UUID modelId, UUID classifierId) {
        UmlModel model = findEditableModel(modelId);
        authorizationService.requireWrite(model.getProject().getId());
        UmlClassifier classifier = resolveClassifier(classifierId, modelId,
                SemanticErrorCode.CLASSIFIER_MODEL_MISMATCH);

        diagramCleanupService.removeClassifierRepresentations(classifierId);

        propertyRepository.findByOwnerClassifierElementIdOrderByElementNameAscElementIdAsc(classifierId)
                .forEach(this::deletePropertyEntity);
        operationRepository.findByOwnerClassifierElementIdOrderByElementNameAscElementIdAsc(classifierId)
                .forEach(this::deleteOperationEntity);
        propertyRepository.flush();
        parameterRepository.flush();
        operationRepository.flush();
        if (propertyRepository.existsByTypeClassifierElementId(classifierId)
                || parameterRepository.existsByTypeClassifierElementId(classifierId)) {
            throw error(SemanticErrorCode.CLASSIFIER_IN_USE, HttpStatus.CONFLICT,
                    "Classifier is used as the type of another feature");
        }
        UmlElement element = classifier.getElement();
        classifierRepository.delete(classifier);
        classifierRepository.flush();
        elementRepository.delete(element);
        model.recordSemanticMutation();
        modelRepository.saveAndFlush(model);
        realtimeEvents.semantic(RealtimeEventType.CLASSIFIER_DELETED, model.getProject().getId(),
                modelId, classifierId, model.getRevision());
    }

    @Transactional
    public UmlPropertyResponse createProperty(
            UUID modelId, UUID classifierId, CreateUmlPropertyCommand command) {
        UmlModel model = findEditableModel(modelId);
        authorizationService.requireWrite(model.getProject().getId());
        UmlClassifier owner = resolveClassifier(classifierId, modelId,
                SemanticErrorCode.CLASSIFIER_MODEL_MISMATCH);
        ResolvedType type = resolveType(
                command.typeKind(), command.primitiveType(), command.typeClassifierId(), modelId);
        int lowerBound = resolveMultiplicity(command.lowerBound(), command.upperBound());
        Visibility visibility = command.visibility() == null ? Visibility.PRIVATE : command.visibility();

        UmlElement element = elementRepository.save(new UmlElement(
                model, UmlElementType.PROPERTY, normalizeName(command.name()), command.documentation()));
        UmlProperty property = propertyRepository.save(new UmlProperty(
                element,
                owner,
                visibility,
                command.typeKind(),
                command.primitiveType(),
                type.classifier(),
                lowerBound,
                command.upperBound(),
                command.staticProperty(),
                command.readOnly(),
                command.derived()));
        model.recordSemanticMutation();
        modelRepository.saveAndFlush(model);
        realtimeEvents.semantic(RealtimeEventType.PROPERTY_CREATED, model.getProject().getId(),
                modelId, property.getElementId(), model.getRevision());
        return toPropertyResponse(property, model.getRevision());
    }

    @Transactional(readOnly = true)
    public List<UmlPropertyResponse> listProperties(UUID modelId, UUID classifierId) {
        UmlModel model = findModel(modelId);
        authorizationService.requireRead(model.getProject().getId());
        resolveClassifier(classifierId, modelId, SemanticErrorCode.CLASSIFIER_MODEL_MISMATCH);
        return propertyRepository
                .findByOwnerClassifierElementIdOrderByElementNameAscElementIdAsc(classifierId)
                .stream()
                .map(property -> toPropertyResponse(property, model.getRevision()))
                .toList();
    }

    @Transactional
    public UmlPropertyResponse updateProperty(UUID modelId, UUID classifierId, UUID propertyId,
            CreateUmlPropertyCommand command) {
        UmlModel model = findEditableModel(modelId);
        authorizationService.requireWrite(model.getProject().getId());
        resolveClassifier(classifierId, modelId, SemanticErrorCode.CLASSIFIER_MODEL_MISMATCH);
        UmlProperty property = resolveProperty(propertyId, modelId, classifierId);
        ResolvedType type = resolveType(command.typeKind(), command.primitiveType(),
                command.typeClassifierId(), modelId);
        int lowerBound = resolveMultiplicity(command.lowerBound(), command.upperBound());
        property.getElement().update(normalizeName(command.name()), command.documentation());
        property.update(command.visibility() == null ? Visibility.PRIVATE : command.visibility(),
                command.typeKind(), command.primitiveType(), type.classifier(), lowerBound,
                command.upperBound(), command.staticProperty(), command.readOnly(), command.derived());
        model.recordSemanticMutation();
        modelRepository.saveAndFlush(model);
        realtimeEvents.semantic(RealtimeEventType.PROPERTY_UPDATED, model.getProject().getId(),
                modelId, propertyId, model.getRevision());
        return toPropertyResponse(property, model.getRevision());
    }

    @Transactional
    public void deleteProperty(UUID modelId, UUID classifierId, UUID propertyId) {
        UmlModel model = findEditableModel(modelId);
        authorizationService.requireWrite(model.getProject().getId());
        resolveClassifier(classifierId, modelId, SemanticErrorCode.CLASSIFIER_MODEL_MISMATCH);
        deletePropertyEntity(resolveProperty(propertyId, modelId, classifierId));
        model.recordSemanticMutation();
        modelRepository.saveAndFlush(model);
        realtimeEvents.semantic(RealtimeEventType.PROPERTY_DELETED, model.getProject().getId(),
                modelId, propertyId, model.getRevision());
    }

    @Transactional
    public UmlOperationResponse createOperation(
            UUID modelId, UUID classifierId, CreateUmlOperationCommand command) {
        UmlModel model = findEditableModel(modelId);
        authorizationService.requireWrite(model.getProject().getId());
        UmlClassifier owner = resolveClassifier(classifierId, modelId,
                SemanticErrorCode.CLASSIFIER_MODEL_MISMATCH);
        Visibility visibility = command.visibility() == null ? Visibility.PUBLIC : command.visibility();

        UmlElement element = elementRepository.save(new UmlElement(
                model, UmlElementType.OPERATION, normalizeName(command.name()), command.documentation()));
        UmlOperation operation = operationRepository.save(new UmlOperation(
                element,
                owner,
                visibility,
                command.abstractOperation(),
                command.staticOperation(),
                command.query()));
        model.recordSemanticMutation();
        modelRepository.saveAndFlush(model);
        realtimeEvents.semantic(RealtimeEventType.OPERATION_CREATED, model.getProject().getId(),
                modelId, operation.getElementId(), model.getRevision());
        return toOperationResponse(operation, model.getRevision());
    }

    @Transactional(readOnly = true)
    public List<UmlOperationResponse> listOperations(UUID modelId, UUID classifierId) {
        UmlModel model = findModel(modelId);
        authorizationService.requireRead(model.getProject().getId());
        resolveClassifier(classifierId, modelId, SemanticErrorCode.CLASSIFIER_MODEL_MISMATCH);
        return operationRepository
                .findByOwnerClassifierElementIdOrderByElementNameAscElementIdAsc(classifierId)
                .stream()
                .map(operation -> toOperationResponse(operation, model.getRevision()))
                .toList();
    }

    @Transactional
    public UmlOperationResponse updateOperation(UUID modelId, UUID classifierId, UUID operationId,
            CreateUmlOperationCommand command) {
        UmlModel model = findEditableModel(modelId);
        authorizationService.requireWrite(model.getProject().getId());
        resolveClassifier(classifierId, modelId, SemanticErrorCode.CLASSIFIER_MODEL_MISMATCH);
        UmlOperation operation = resolveOperation(operationId, modelId, classifierId);
        operation.getElement().update(normalizeName(command.name()), command.documentation());
        operation.update(command.visibility() == null ? Visibility.PUBLIC : command.visibility(),
                command.abstractOperation(), command.staticOperation(), command.query());
        model.recordSemanticMutation();
        modelRepository.saveAndFlush(model);
        realtimeEvents.semantic(RealtimeEventType.OPERATION_UPDATED, model.getProject().getId(),
                modelId, operationId, model.getRevision());
        return toOperationResponse(operation, model.getRevision());
    }

    @Transactional
    public void deleteOperation(UUID modelId, UUID classifierId, UUID operationId) {
        UmlModel model = findEditableModel(modelId);
        authorizationService.requireWrite(model.getProject().getId());
        resolveClassifier(classifierId, modelId, SemanticErrorCode.CLASSIFIER_MODEL_MISMATCH);
        deleteOperationEntity(resolveOperation(operationId, modelId, classifierId));
        model.recordSemanticMutation();
        modelRepository.saveAndFlush(model);
        realtimeEvents.semantic(RealtimeEventType.OPERATION_DELETED, model.getProject().getId(),
                modelId, operationId, model.getRevision());
    }

    @Transactional
    public UmlParameterResponse createParameter(
            UUID modelId, UUID operationId, CreateUmlParameterCommand command) {
        UmlModel model = findEditableModel(modelId);
        authorizationService.requireWrite(model.getProject().getId());
        UmlOperation operation = resolveOperation(operationId, modelId);
        validateParameterCommand(command, operationId, null);
        ResolvedType type = resolveType(
                command.typeKind(), command.primitiveType(), command.typeClassifierId(), modelId);
        int lowerBound = resolveMultiplicity(command.lowerBound(), command.upperBound());

        UmlElement element = elementRepository.save(new UmlElement(
                model,
                UmlElementType.PARAMETER,
                normalizeOptionalName(command.name()),
                command.documentation()));
        UmlParameter parameter = parameterRepository.save(new UmlParameter(
                element,
                operation,
                command.direction(),
                command.typeKind(),
                command.primitiveType(),
                type.classifier(),
                lowerBound,
                command.upperBound(),
                command.position()));
        model.recordSemanticMutation();
        modelRepository.saveAndFlush(model);
        realtimeEvents.semantic(RealtimeEventType.PARAMETER_CREATED, model.getProject().getId(),
                modelId, parameter.getElementId(), model.getRevision());
        return toParameterResponse(parameter, model.getRevision());
    }

    @Transactional(readOnly = true)
    public List<UmlParameterResponse> listParameters(UUID modelId, UUID operationId) {
        UmlModel model = findModel(modelId);
        authorizationService.requireRead(model.getProject().getId());
        resolveOperation(operationId, modelId);
        return parameterRepository.findByOperationElementIdOrderByPositionAsc(operationId).stream()
                .map(parameter -> toParameterResponse(parameter, model.getRevision()))
                .toList();
    }

    @Transactional
    public UmlParameterResponse updateParameter(UUID modelId, UUID operationId, UUID parameterId,
            CreateUmlParameterCommand command) {
        UmlModel model = findEditableModel(modelId);
        authorizationService.requireWrite(model.getProject().getId());
        UmlOperation operation = resolveOperation(operationId, modelId);
        UmlParameter parameter = resolveParameter(parameterId, modelId, operationId);
        validateParameterCommand(command, operationId, parameter);
        ResolvedType type = resolveType(command.typeKind(), command.primitiveType(),
                command.typeClassifierId(), modelId);
        int lowerBound = resolveMultiplicity(command.lowerBound(), command.upperBound());
        parameter.getElement().update(normalizeOptionalName(command.name()), command.documentation());
        parameter.update(command.direction(), command.typeKind(), command.primitiveType(), type.classifier(),
                lowerBound, command.upperBound(), command.position());
        model.recordSemanticMutation();
        modelRepository.saveAndFlush(model);
        realtimeEvents.semantic(RealtimeEventType.PARAMETER_UPDATED, model.getProject().getId(),
                modelId, parameterId, model.getRevision());
        return toParameterResponse(parameter, model.getRevision());
    }

    @Transactional
    public void deleteParameter(UUID modelId, UUID operationId, UUID parameterId) {
        UmlModel model = findEditableModel(modelId);
        authorizationService.requireWrite(model.getProject().getId());
        resolveOperation(operationId, modelId);
        UmlParameter parameter = resolveParameter(parameterId, modelId, operationId);
        UmlElement element = parameter.getElement();
        parameterRepository.delete(parameter);
        parameterRepository.flush();
        elementRepository.delete(element);
        model.recordSemanticMutation();
        modelRepository.saveAndFlush(model);
        realtimeEvents.semantic(RealtimeEventType.PARAMETER_DELETED, model.getProject().getId(),
                modelId, parameterId, model.getRevision());
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

    private UmlPackage resolvePackage(
            UUID packageId, UUID modelId, SemanticErrorCode mismatchCode) {
        if (packageId == null) {
            return null;
        }
        UmlPackage umlPackage = packageRepository.findById(packageId)
                .orElseThrow(() -> error(SemanticErrorCode.PACKAGE_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Package does not exist"));
        if (!umlPackage.getElement().getModel().getId().equals(modelId)) {
            throw error(mismatchCode, HttpStatus.CONFLICT,
                    "Package belongs to a different UML model");
        }
        return umlPackage;
    }

    private UmlClassifier resolveClassifier(
            UUID classifierId, UUID modelId, SemanticErrorCode mismatchCode) {
        UmlClassifier classifier = classifierRepository.findById(classifierId)
                .orElseThrow(() -> error(SemanticErrorCode.CLASSIFIER_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Classifier does not exist"));
        if (!classifier.getElement().getModel().getId().equals(modelId)) {
            throw error(mismatchCode, HttpStatus.CONFLICT,
                    "Classifier belongs to a different UML model");
        }
        return classifier;
    }

    private UmlProperty resolveProperty(UUID propertyId, UUID modelId, UUID classifierId) {
        UmlProperty property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> error(SemanticErrorCode.PROPERTY_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Property does not exist"));
        if (!property.getElement().getModel().getId().equals(modelId)
                || !property.getOwnerClassifier().getElementId().equals(classifierId)) {
            throw error(SemanticErrorCode.CLASSIFIER_MODEL_MISMATCH, HttpStatus.CONFLICT,
                    "Property does not belong to the requested classifier and model");
        }
        return property;
    }

    private UmlOperation resolveOperation(UUID operationId, UUID modelId) {
        UmlOperation operation = operationRepository.findById(operationId)
                .orElseThrow(() -> error(SemanticErrorCode.OPERATION_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Operation does not exist"));
        if (!operation.getElement().getModel().getId().equals(modelId)) {
            throw error(SemanticErrorCode.OPERATION_MODEL_MISMATCH, HttpStatus.CONFLICT,
                    "Operation belongs to a different UML model");
        }
        return operation;
    }

    private UmlOperation resolveOperation(UUID operationId, UUID modelId, UUID classifierId) {
        UmlOperation operation = resolveOperation(operationId, modelId);
        if (!operation.getOwnerClassifier().getElementId().equals(classifierId)) {
            throw error(SemanticErrorCode.CLASSIFIER_MODEL_MISMATCH, HttpStatus.CONFLICT,
                    "Operation does not belong to the requested classifier");
        }
        return operation;
    }

    private UmlParameter resolveParameter(UUID parameterId, UUID modelId, UUID operationId) {
        UmlParameter parameter = parameterRepository.findById(parameterId)
                .orElseThrow(() -> error(SemanticErrorCode.PARAMETER_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Parameter does not exist"));
        if (!parameter.getElement().getModel().getId().equals(modelId)
                || !parameter.getOperation().getElementId().equals(operationId)) {
            throw error(SemanticErrorCode.OPERATION_MODEL_MISMATCH, HttpStatus.CONFLICT,
                    "Parameter does not belong to the requested operation and model");
        }
        return parameter;
    }

    private void validateParameterCommand(
            CreateUmlParameterCommand command, UUID operationId, UmlParameter current) {
        if (command.direction() == null) {
            throw error(SemanticErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                    "Parameter direction is required");
        }
        if (command.position() == null || command.position() < 0) {
            throw error(SemanticErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                    "Parameter position must not be negative");
        }
        if ((current == null || current.getPosition() != command.position())
                && parameterRepository.existsByOperationElementIdAndPosition(operationId, command.position())) {
            throw error(SemanticErrorCode.DUPLICATE_PARAMETER_POSITION, HttpStatus.CONFLICT,
                    "A parameter already uses this operation position");
        }
        if (command.direction() == ParameterDirection.RETURN
                && (current == null || current.getDirection() != ParameterDirection.RETURN)
                && parameterRepository.existsByOperationElementIdAndDirection(
                        operationId, ParameterDirection.RETURN)) {
            throw error(SemanticErrorCode.RETURN_PARAMETER_ALREADY_EXISTS, HttpStatus.CONFLICT,
                    "An operation can have at most one RETURN parameter");
        }
    }

    private void ensureClassifierNameAvailable(
            UUID modelId, UmlPackage umlPackage, String name, UUID excludedClassifierId) {
        UUID packageId = umlPackage == null ? null : umlPackage.getElementId();
        boolean duplicate = classifierRepository.findByElementModelIdOrderByElementNameAsc(modelId).stream()
                .filter(classifier -> excludedClassifierId == null
                        || !classifier.getElementId().equals(excludedClassifierId))
                .filter(classifier -> java.util.Objects.equals(
                        classifier.getUmlPackage() == null
                                ? null : classifier.getUmlPackage().getElementId(), packageId))
                .anyMatch(classifier -> classifier.getElement().getName().equalsIgnoreCase(name));
        if (duplicate) {
            throw error(SemanticErrorCode.CLASSIFIER_NAME_ALREADY_EXISTS, HttpStatus.CONFLICT,
                    "A Classifier with this name already exists in the UML package");
        }
    }

    private void deletePropertyEntity(UmlProperty property) {
        UmlElement element = property.getElement();
        propertyRepository.delete(property);
        propertyRepository.flush();
        elementRepository.delete(element);
    }

    private void deleteOperationEntity(UmlOperation operation) {
        parameterRepository.findByOperationElementIdOrderByPositionAsc(operation.getElementId())
                .forEach(parameter -> {
                    UmlElement parameterElement = parameter.getElement();
                    parameterRepository.delete(parameter);
                    parameterRepository.flush();
                    elementRepository.delete(parameterElement);
                });
        UmlElement element = operation.getElement();
        operationRepository.delete(operation);
        operationRepository.flush();
        elementRepository.delete(element);
    }

    private ResolvedType resolveType(
            TypeKind typeKind,
            com.casegen.modeling.domain.PrimitiveType primitiveType,
            UUID typeClassifierId,
            UUID modelId) {
        if (typeKind == TypeKind.PRIMITIVE && primitiveType != null && typeClassifierId == null) {
            return new ResolvedType(null);
        }
        if (typeKind == TypeKind.CLASSIFIER && primitiveType == null && typeClassifierId != null) {
            return new ResolvedType(resolveClassifier(typeClassifierId, modelId,
                    SemanticErrorCode.TYPE_CLASSIFIER_MODEL_MISMATCH));
        }
        throw error(SemanticErrorCode.INVALID_TYPE_REFERENCE, HttpStatus.BAD_REQUEST,
                "Type kind must select exactly one compatible type reference");
    }

    private int resolveMultiplicity(Integer lowerBound, Integer upperBound) {
        int resolvedLower = lowerBound == null ? 1 : lowerBound;
        if (resolvedLower < 0 || (upperBound != null && upperBound < resolvedLower)) {
            throw error(SemanticErrorCode.INVALID_MULTIPLICITY, HttpStatus.BAD_REQUEST,
                    "Multiplicity requires lower >= 0 and unlimited or upper >= lower");
        }
        return resolvedLower;
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw error(SemanticErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                    "Name is required");
        }
        String normalized = name.trim();
        if (normalized.length() > 180) {
            throw error(SemanticErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                    "Name must not exceed 180 characters");
        }
        return normalized;
    }

    private String normalizeOptionalName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return normalizeName(name);
    }

    private SemanticException error(SemanticErrorCode code, HttpStatus status, String message) {
        return new SemanticException(code, status, message);
    }

    private UmlModelResponse toModelResponse(UmlModel model) {
        return new UmlModelResponse(
                model.getId(),
                model.getProject().getId(),
                model.getName(),
                model.getDocumentation(),
                model.getUmlVersion(),
                model.getRevision(),
                model.getLockVersion(),
                model.getStatus(),
                model.getCreatedBy() == null ? null : model.getCreatedBy().getId(),
                model.getCreatedAt(),
                model.getUpdatedAt());
    }

    private UmlPackageResponse toPackageResponse(UmlPackage umlPackage, long modelRevision) {
        UmlElement element = umlPackage.getElement();
        return new UmlPackageResponse(
                element.getId(),
                element.getModel().getId(),
                element.getElementType(),
                element.getName(),
                element.getDocumentation(),
                umlPackage.getParentPackage() == null ? null : umlPackage.getParentPackage().getElementId(),
                modelRevision,
                element.getCreatedAt(),
                element.getUpdatedAt());
    }

    private UmlClassifierResponse toClassifierResponse(UmlClassifier classifier, long modelRevision) {
        UmlElement element = classifier.getElement();
        return new UmlClassifierResponse(
                element.getId(),
                element.getModel().getId(),
                element.getElementType(),
                element.getName(),
                element.getDocumentation(),
                classifier.getUmlPackage() == null ? null : classifier.getUmlPackage().getElementId(),
                classifier.getKind(),
                classifier.getVisibility(),
                classifier.isAbstractClassifier(),
                classifier.isActive(),
                modelRevision,
                element.getCreatedAt(),
                element.getUpdatedAt());
    }

    private UmlPropertyResponse toPropertyResponse(UmlProperty property, long modelRevision) {
        UmlElement element = property.getElement();
        return new UmlPropertyResponse(
                element.getId(),
                element.getModel().getId(),
                element.getElementType(),
                element.getName(),
                element.getDocumentation(),
                property.getOwnerClassifier().getElementId(),
                property.getVisibility(),
                property.getTypeKind(),
                property.getPrimitiveType(),
                property.getTypeClassifier() == null ? null : property.getTypeClassifier().getElementId(),
                property.getLowerBound(),
                property.getUpperBound(),
                property.isStaticProperty(),
                property.isReadOnly(),
                property.isDerived(),
                modelRevision,
                element.getCreatedAt(),
                element.getUpdatedAt());
    }

    private UmlOperationResponse toOperationResponse(UmlOperation operation, long modelRevision) {
        UmlElement element = operation.getElement();
        return new UmlOperationResponse(
                element.getId(),
                element.getModel().getId(),
                element.getElementType(),
                element.getName(),
                element.getDocumentation(),
                operation.getOwnerClassifier().getElementId(),
                operation.getVisibility(),
                operation.isAbstractOperation(),
                operation.isStaticOperation(),
                operation.isQuery(),
                modelRevision,
                element.getCreatedAt(),
                element.getUpdatedAt());
    }

    private UmlParameterResponse toParameterResponse(UmlParameter parameter, long modelRevision) {
        UmlElement element = parameter.getElement();
        return new UmlParameterResponse(
                element.getId(),
                element.getModel().getId(),
                element.getElementType(),
                element.getName(),
                element.getDocumentation(),
                parameter.getOperation().getElementId(),
                parameter.getDirection(),
                parameter.getTypeKind(),
                parameter.getPrimitiveType(),
                parameter.getTypeClassifier() == null ? null : parameter.getTypeClassifier().getElementId(),
                parameter.getLowerBound(),
                parameter.getUpperBound(),
                parameter.getPosition(),
                modelRevision,
                element.getCreatedAt(),
                element.getUpdatedAt());
    }

    private record ResolvedType(UmlClassifier classifier) {
    }
}
