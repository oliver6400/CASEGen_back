package com.casegen.modeling.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.casegen.modeling.command.CreateUmlClassifierCommand;
import com.casegen.modeling.command.CreateUmlPackageCommand;
import com.casegen.modeling.domain.ClassifierKind;
import com.casegen.modeling.domain.UmlClassifier;
import com.casegen.modeling.domain.UmlElement;
import com.casegen.modeling.domain.UmlElementType;
import com.casegen.modeling.domain.UmlModel;
import com.casegen.modeling.domain.UmlPackage;
import com.casegen.modeling.domain.Visibility;
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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class UmlModelServiceTest {

    private ProjectRepository projectRepository;
    private UmlModelRepository modelRepository;
    private UmlElementRepository elementRepository;
    private UmlPackageRepository packageRepository;
    private UmlClassifierRepository classifierRepository;
    private UmlPropertyRepository propertyRepository;
    private UmlOperationRepository operationRepository;
    private UmlParameterRepository parameterRepository;
    private UmlModelService service;
    private ProjectAuthorizationService authorizationService;
    private com.casegen.collaboration.RealtimeEventPublisher realtimeEvents;

    @BeforeEach
    void setUp() {
        projectRepository = Mockito.mock(ProjectRepository.class);
        modelRepository = Mockito.mock(UmlModelRepository.class);
        elementRepository = Mockito.mock(UmlElementRepository.class);
        packageRepository = Mockito.mock(UmlPackageRepository.class);
        classifierRepository = Mockito.mock(UmlClassifierRepository.class);
        propertyRepository = Mockito.mock(UmlPropertyRepository.class);
        operationRepository = Mockito.mock(UmlOperationRepository.class);
        parameterRepository = Mockito.mock(UmlParameterRepository.class);
        authorizationService = Mockito.mock(ProjectAuthorizationService.class);
        realtimeEvents = Mockito.mock(com.casegen.collaboration.RealtimeEventPublisher.class);
        service = new UmlModelService(projectRepository, modelRepository, elementRepository,
                packageRepository, classifierRepository, propertyRepository,
                operationRepository, parameterRepository, authorizationService,
                Mockito.mock(DiagramRepresentationCleanupService.class),
                realtimeEvents);
    }

    @Test
    void incrementsRevisionExactlyOnceWhenCreatingPackage() {
        UUID modelId = UUID.randomUUID();
        UmlModel model = new UmlModel(new Project("Project", null), "Domain", null, null);
        when(modelRepository.findByIdForUpdate(modelId)).thenReturn(Optional.of(model));
        when(elementRepository.save(any(UmlElement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(packageRepository.save(any(UmlPackage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(modelRepository.saveAndFlush(model)).thenReturn(model);

        var response = service.createPackage(modelId, new CreateUmlPackageCommand("sales", null, null));

        assertEquals(1, model.getRevision());
        assertEquals(1, response.modelRevision());
        assertEquals(UmlElementType.PACKAGE, response.elementType());
        verify(realtimeEvents).semantic(
                com.casegen.collaboration.RealtimeEventType.PACKAGE_CREATED,
                model.getProject().getId(), modelId, response.id(), 1L);
    }

    @Test
    void rejectsActiveInterfaceBeforePersistingRootElement() {
        UUID modelId = UUID.randomUUID();
        UmlModel model = new UmlModel(new Project("Project", null), "Domain", null, null);
        when(modelRepository.findByIdForUpdate(modelId)).thenReturn(Optional.of(model));

        SemanticException exception = assertThrows(SemanticException.class,
                () -> service.createClassifier(modelId, new CreateUmlClassifierCommand(
                        "Identifiable", null, null, ClassifierKind.INTERFACE,
                        Visibility.PUBLIC, false, true)));

        assertEquals(SemanticErrorCode.INTERFACE_CANNOT_BE_ACTIVE, exception.getCode());
        assertEquals(0, model.getRevision());
        verify(elementRepository, never()).save(any());
        verify(classifierRepository, never()).save(any(UmlClassifier.class));
    }

    @Test
    void rejectsMutationWhenModelIsArchived() {
        UUID modelId = UUID.randomUUID();
        UmlModel model = new UmlModel(new Project("Project", null), "Domain", null, null);
        model.archive();
        when(modelRepository.findByIdForUpdate(modelId)).thenReturn(Optional.of(model));

        SemanticException exception = assertThrows(SemanticException.class,
                () -> service.createPackage(modelId, new CreateUmlPackageCommand("domain", null, null)));

        assertEquals(SemanticErrorCode.MODEL_NOT_EDITABLE, exception.getCode());
        verify(elementRepository, never()).save(any());
    }

    @Test
    void rejectsClassifierPackageFromAnotherModel() {
        UUID modelId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        UmlModel targetModel = new UmlModel(new Project("Project", null), "Target", null, null);
        UmlModel otherModel = Mockito.mock(UmlModel.class);
        UmlElement packageElement = Mockito.mock(UmlElement.class);
        UmlPackage umlPackage = Mockito.mock(UmlPackage.class);
        when(modelRepository.findByIdForUpdate(modelId)).thenReturn(Optional.of(targetModel));
        when(packageRepository.findById(packageId)).thenReturn(Optional.of(umlPackage));
        when(umlPackage.getElement()).thenReturn(packageElement);
        when(packageElement.getModel()).thenReturn(otherModel);
        when(otherModel.getId()).thenReturn(UUID.randomUUID());

        SemanticException exception = assertThrows(SemanticException.class,
                () -> service.createClassifier(modelId, new CreateUmlClassifierCommand(
                        "Customer", null, packageId, ClassifierKind.CLASS,
                        Visibility.PUBLIC, false, false)));

        assertEquals(SemanticErrorCode.CLASSIFIER_PACKAGE_MODEL_MISMATCH, exception.getCode());
        verify(elementRepository, never()).save(any());
    }
}
