package com.casegen.interoperability.image;

import static com.casegen.interoperability.image.ImageImportReconciliationPreviewResponse.Action.*;
import static com.casegen.interoperability.image.ImageImportReconciliationPreviewResponse.ElementKind.CLASSIFIER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.casegen.interoperability.image.DetectedClassDiagram.DetectedClassifier;
import com.casegen.interoperability.image.ImageImportExistingConfirmRequest.Decision;
import com.casegen.interoperability.image.ImageImportReconciliationPreviewResponse.Candidate;
import com.casegen.interoperability.image.ImageImportReconciliationPreviewResponse.ReconciliationItem;
import com.casegen.modeling.domain.Diagram;
import com.casegen.modeling.domain.UmlModel;
import com.casegen.modeling.domain.UmlModelStatus;
import com.casegen.modeling.dto.DiagramNodeResponse;
import com.casegen.modeling.dto.UmlClassifierResponse;
import com.casegen.modeling.repository.DiagramNodeRepository;
import com.casegen.modeling.repository.DiagramRepository;
import com.casegen.modeling.repository.UmlModelRepository;
import com.casegen.modeling.repository.UmlPackageRepository;
import com.casegen.modeling.service.DiagramService;
import com.casegen.modeling.service.UmlModelService;
import com.casegen.modeling.service.UmlRelationshipService;
import com.casegen.project.domain.Project;
import com.casegen.project.service.ProjectAuthorizationService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

class ImageImportExistingConfirmationServiceTest {
    private final ProjectAuthorizationService authorization = mock(ProjectAuthorizationService.class);
    private final UmlModelRepository models = mock(UmlModelRepository.class);
    private final DiagramRepository diagrams = mock(DiagramRepository.class);
    private final UmlPackageRepository packages = mock(UmlPackageRepository.class);
    private final DiagramNodeRepository nodes = mock(DiagramNodeRepository.class);
    private final ImageImportReconciliationPreviewService previews =
            mock(ImageImportReconciliationPreviewService.class);
    private final UmlModelService umlModels = mock(UmlModelService.class);
    private final UmlRelationshipService relationships = mock(UmlRelationshipService.class);
    private final DiagramService diagramService = mock(DiagramService.class);
    private final ImageImportExistingConfirmationService service =
            new ImageImportExistingConfirmationService(authorization, models, diagrams, packages,
                    nodes, previews, umlModels, relationships, diagramService);

    private UUID projectId;
    private UUID modelId;
    private UUID diagramId;
    private UmlModel model;
    private Diagram diagram;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        modelId = UUID.randomUUID();
        diagramId = UUID.randomUUID();
        Project project = mock(Project.class);
        when(project.getId()).thenReturn(projectId);
        model = mock(UmlModel.class);
        when(model.getId()).thenReturn(modelId);
        when(model.getProject()).thenReturn(project);
        when(model.getRevision()).thenReturn(3L);
        when(model.getStatus()).thenReturn(UmlModelStatus.EDITABLE);
        diagram = mock(Diagram.class);
        when(diagram.getId()).thenReturn(diagramId);
        when(diagram.getModel()).thenReturn(model);
        when(diagram.getRevision()).thenReturn(5L);
        when(models.findByIdForUpdate(modelId)).thenReturn(Optional.of(model));
        when(diagrams.findByIdForUpdate(diagramId)).thenReturn(Optional.of(diagram));
        when(nodes.findByDiagramIdOrderByZIndexAscCreatedAtAsc(diagramId)).thenReturn(List.of());
    }

    @Test
    void createsClassifierAndNodeOnlyAfterCurrentPreviewIsValidated() {
        UUID createdId = UUID.randomUUID();
        when(previews.preview(any(), any())).thenReturn(preview(CREATE, List.of()));
        UmlClassifierResponse classifier = mock(UmlClassifierResponse.class);
        when(classifier.id()).thenReturn(createdId);
        when(umlModels.createClassifier(any(), any())).thenReturn(classifier);
        DiagramNodeResponse node = new DiagramNodeResponse(UUID.randomUUID(), createdId,
                50, 50, 310, 180, 0, null, null);
        when(diagramService.addNode(any(), any())).thenReturn(node);

        ImageImportExistingConfirmResponse result = service.confirm(projectId,
                request(new Decision("c1", CREATE, null)));

        assertEquals(createdId, result.createdIds().get("c1"));
        assertEquals(1, result.createdElementCount());
        assertEquals(1, result.createdNodeCount());
        var order = inOrder(models, diagrams, previews, umlModels, diagramService);
        order.verify(models).findByIdForUpdate(modelId);
        order.verify(diagrams).findByIdForUpdate(diagramId);
        order.verify(previews).preview(any(), any());
        order.verify(umlModels).createClassifier(any(), any());
        order.verify(diagramService).addNode(any(), any());
    }

    @Test
    void mapsOnlyToCandidateReturnedByFreshPreview() {
        UUID existingId = UUID.randomUUID();
        when(previews.preview(any(), any())).thenReturn(preview(MAP_TO_EXISTING,
                List.of(new Candidate(existingId, "Producto", "CLASS", null, null))));
        when(diagramService.addNode(any(), any())).thenReturn(new DiagramNodeResponse(
                UUID.randomUUID(), existingId, 50, 50, 310, 180, 0, null, null));

        ImageImportExistingConfirmResponse result = service.confirm(projectId,
                request(new Decision("c1", MAP_TO_EXISTING, existingId)));

        assertEquals(existingId, result.reusedIds().get("c1"));
        verify(umlModels, never()).createClassifier(any(), any());
    }

    @Test
    void rejectsStaleRevisionBeforeRecomputingPreviewOrWriting() {
        when(model.getRevision()).thenReturn(4L);

        ImageImportException error = assertThrows(ImageImportException.class,
                () -> service.confirm(projectId, request(new Decision("c1", CREATE, null))));

        assertEquals("IMAGE_IMPORT_STALE_MODEL_REVISION", error.getCode());
        assertEquals(409, error.getStatus().value());
        verify(previews, never()).preview(any(), any());
        verify(umlModels, never()).createClassifier(any(), any());
    }

    @Test
    void rejectsStaleDiagramRevisionUnderLock() {
        when(diagram.getRevision()).thenReturn(6L);

        ImageImportException error = assertThrows(ImageImportException.class,
                () -> service.confirm(projectId, request(new Decision("c1", CREATE, null))));

        assertEquals("IMAGE_IMPORT_STALE_DIAGRAM_REVISION", error.getCode());
        assertEquals(409, error.getStatus().value());
        verify(previews, never()).preview(any(), any());
    }

    @Test
    void rejectsCandidateNotPresentInFreshPreview() {
        UUID currentCandidate = UUID.randomUUID();
        when(previews.preview(any(), any())).thenReturn(preview(MAP_TO_EXISTING,
                List.of(new Candidate(currentCandidate, "Producto", "CLASS", null, null))));

        ImageImportException error = assertThrows(ImageImportException.class,
                () -> service.confirm(projectId,
                        request(new Decision("c1", MAP_TO_EXISTING, UUID.randomUUID()))));

        assertEquals("IMAGE_IMPORT_INVALID_CANDIDATE", error.getCode());
        verify(umlModels, never()).createClassifier(any(), any());
    }

    @Test
    void rejectsUnresolvedConflictAndInvalidOrDuplicateDecisions() {
        when(previews.preview(any(), any())).thenReturn(preview(CONFLICT, List.of()));
        ImageImportException unresolved = assertThrows(ImageImportException.class,
                () -> service.confirm(projectId, request(new Decision("c1", CONFLICT, null))));
        assertEquals("IMAGE_IMPORT_UNRESOLVED_CONFLICT", unresolved.getCode());

        when(previews.preview(any(), any())).thenReturn(preview(CREATE, List.of()));
        var duplicate = new ImageImportExistingConfirmRequest(modelId, diagramId, null, 3, 5,
                proposal(), List.of(new Decision("c1", CREATE, null),
                        new Decision("c1", SKIP, null)));
        ImageImportException duplicated = assertThrows(ImageImportException.class,
                () -> service.confirm(projectId, duplicate));
        assertEquals("IMAGE_IMPORT_DUPLICATE_DECISION_ID", duplicated.getCode());
    }

    @Test
    void downstreamFailureStopsDiagramWritesAndMethodDefinesTransactionBoundary() throws Exception {
        when(previews.preview(any(), any())).thenReturn(preview(CREATE, List.of()));
        when(umlModels.createClassifier(any(), any())).thenThrow(new IllegalStateException("failure"));

        assertThrows(IllegalStateException.class,
                () -> service.confirm(projectId, request(new Decision("c1", CREATE, null))));
        verify(diagramService, never()).addNode(any(), any());
        assertTrue(ImageImportExistingConfirmationService.class
                .getMethod("confirm", UUID.class, ImageImportExistingConfirmRequest.class)
                .isAnnotationPresent(Transactional.class));
    }

    @Test
    void viewerIsRejectedBeforeAnyTargetIsLocked() {
        doThrow(new AccessDeniedException("viewer")).when(authorization).requireWrite(projectId);

        assertThrows(AccessDeniedException.class,
                () -> service.confirm(projectId, request(new Decision("c1", CREATE, null))));
        verify(models, never()).findByIdForUpdate(any());
        verify(previews, never()).preview(any(), any());
    }

    @Test
    void rejectsModelFromAnotherProjectWithoutLockingDiagram() {
        Project foreign = mock(Project.class);
        when(foreign.getId()).thenReturn(UUID.randomUUID());
        when(model.getProject()).thenReturn(foreign);

        ImageImportException error = assertThrows(ImageImportException.class,
                () -> service.confirm(projectId, request(new Decision("c1", CREATE, null))));

        assertEquals("IMAGE_IMPORT_TARGET_MODEL_NOT_FOUND", error.getCode());
        verify(diagrams, never()).findByIdForUpdate(any());
    }

    private ImageImportExistingConfirmRequest request(Decision... decisions) {
        return new ImageImportExistingConfirmRequest(modelId, diagramId, null, 3, 5,
                proposal(), List.of(decisions));
    }

    private DetectedClassDiagram proposal() {
        return new DetectedClassDiagram("Ventas", List.of(
                new DetectedClassifier("c1", "Producto", "CLASS", "PUBLIC", .9)),
                List.of(), List.of(), List.of(), List.of(), .9);
    }

    private ImageImportReconciliationPreviewResponse preview(
            ImageImportReconciliationPreviewResponse.Action action, List<Candidate> candidates) {
        return new ImageImportReconciliationPreviewResponse(projectId, modelId, diagramId,
                3, 5, List.of(new ReconciliationItem("c1", CLASSIFIER, "Producto", action,
                action != CREATE, "TEST", "test", candidates, List.of())), List.of());
    }
}
