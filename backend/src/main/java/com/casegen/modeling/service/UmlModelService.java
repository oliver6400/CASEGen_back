package com.casegen.modeling.service;

import com.casegen.modeling.command.CreateUmlClassifierCommand;
import com.casegen.modeling.command.CreateUmlModelCommand;
import com.casegen.modeling.command.CreateUmlPackageCommand;
import com.casegen.modeling.domain.ClassifierKind;
import com.casegen.modeling.domain.UmlClassifier;
import com.casegen.modeling.domain.UmlElement;
import com.casegen.modeling.domain.UmlElementType;
import com.casegen.modeling.domain.UmlModel;
import com.casegen.modeling.domain.UmlModelStatus;
import com.casegen.modeling.domain.UmlPackage;
import com.casegen.modeling.domain.Visibility;
import com.casegen.modeling.dto.UmlClassifierResponse;
import com.casegen.modeling.dto.UmlModelResponse;
import com.casegen.modeling.dto.UmlPackageResponse;
import com.casegen.modeling.exception.SemanticErrorCode;
import com.casegen.modeling.exception.SemanticException;
import com.casegen.modeling.repository.UmlClassifierRepository;
import com.casegen.modeling.repository.UmlElementRepository;
import com.casegen.modeling.repository.UmlModelRepository;
import com.casegen.modeling.repository.UmlPackageRepository;
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
    private final ProjectAuthorizationService authorizationService;

    public UmlModelService(
            ProjectRepository projectRepository,
            UmlModelRepository modelRepository,
            UmlElementRepository elementRepository,
            UmlPackageRepository packageRepository,
            UmlClassifierRepository classifierRepository,
            ProjectAuthorizationService authorizationService) {
        this.projectRepository = projectRepository;
        this.modelRepository = modelRepository;
        this.elementRepository = elementRepository;
        this.packageRepository = packageRepository;
        this.classifierRepository = classifierRepository;
        this.authorizationService = authorizationService;
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

        UmlElement element = elementRepository.save(new UmlElement(
                model, UmlElementType.CLASSIFIER, normalizeName(command.name()), command.documentation()));
        UmlClassifier classifier = classifierRepository.save(new UmlClassifier(
                element, umlPackage, command.kind(), visibility, command.abstractClassifier(), command.active()));
        model.recordSemanticMutation();
        modelRepository.saveAndFlush(model);
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

    private UmlModel findModel(UUID modelId) {
        return modelRepository.findById(modelId)
                .orElseThrow(() -> error(SemanticErrorCode.UML_MODEL_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "UML model does not exist"));
    }

    private UmlModel findEditableModel(UUID modelId) {
        UmlModel model = findModel(modelId);
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
}
