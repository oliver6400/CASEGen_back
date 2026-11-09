package com.casegen.interoperability.image;

import static com.casegen.interoperability.image.ImageImportReconciliationPreviewResponse.Action.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.casegen.interoperability.image.DetectedClassDiagram.*;
import com.casegen.modeling.domain.*;
import com.casegen.modeling.repository.*;
import com.casegen.project.domain.Project;
import com.casegen.project.service.ProjectAuthorizationService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class ImageImportReconciliationPreviewServiceTest {
    private final ProjectAuthorizationService authorization = mock(ProjectAuthorizationService.class);
    private final UmlModelRepository models = mock(UmlModelRepository.class);
    private final DiagramRepository diagrams = mock(DiagramRepository.class);
    private final UmlClassifierRepository classifiers = mock(UmlClassifierRepository.class);
    private final UmlPackageRepository packages = mock(UmlPackageRepository.class);
    private final UmlPropertyRepository properties = mock(UmlPropertyRepository.class);
    private final UmlOperationRepository operations = mock(UmlOperationRepository.class);
    private final UmlParameterRepository parameters = mock(UmlParameterRepository.class);
    private final UmlAssociationRepository associations = mock(UmlAssociationRepository.class);
    private final UmlAssociationEndRepository associationEnds = mock(UmlAssociationEndRepository.class);
    private final UmlGeneralizationRepository generalizations = mock(UmlGeneralizationRepository.class);
    private final UmlRealizationRepository realizations = mock(UmlRealizationRepository.class);
    private final DiagramNodeRepository nodes = mock(DiagramNodeRepository.class);
    private final ImageImportReconciliationPreviewService service =
            new ImageImportReconciliationPreviewService(authorization, models, diagrams, classifiers,
                    packages, properties, operations, parameters, associations, associationEnds,
                    generalizations, realizations, nodes);

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
        diagram = mock(Diagram.class);
        when(diagram.getId()).thenReturn(diagramId);
        when(diagram.getModel()).thenReturn(model);
        when(diagram.getRevision()).thenReturn(5L);
        when(models.findById(modelId)).thenReturn(Optional.of(model));
        when(diagrams.findById(diagramId)).thenReturn(Optional.of(diagram));
    }

    @Test
    void validModelAndDiagramSuggestCreateForNewClassifierWithoutWriting() {
        var result = service.preview(projectId, request(proposal(List.of(classifier("new", "Producto")),
                List.of(), List.of(), List.of())));

        assertEquals(CREATE, result.items().getFirst().suggestedAction());
        assertEquals(3, result.modelRevision());
        assertEquals(5, result.diagramRevision());
        verify(models, never()).save(any());
        verify(diagrams, never()).save(any());
        verify(classifiers, never()).save(any());
        verify(properties, never()).save(any());
        verify(operations, never()).save(any());
        verify(associations, never()).save(any());
        verify(nodes, never()).save(any());
    }

    @Test
    void uniqueNamePackageAndKindSuggestMappingButRequireHumanDecision() {
        UmlClassifier existing = existingClassifier("Producto", ClassifierKind.CLASS);
        when(classifiers.findByElementModelIdOrderByElementNameAsc(modelId)).thenReturn(List.of(existing));

        var item = service.preview(projectId, request(proposal(List.of(classifier("c1", " producto ")),
                List.of(), List.of(), List.of()))).items().getFirst();

        assertEquals(MAP_TO_EXISTING, item.suggestedAction());
        assertEquals(true, item.decisionRequired());
        assertEquals(existing.getElementId(), item.candidates().getFirst().elementId());
    }

    @Test
    void ambiguousClassifierNameIsConflict() {
        UmlClassifier first = existingClassifier("Pedido", ClassifierKind.CLASS);
        UmlClassifier second = existingClassifier("PEDIDO", ClassifierKind.CLASS);
        when(classifiers.findByElementModelIdOrderByElementNameAsc(modelId))
                .thenReturn(List.of(first, second));

        var item = service.preview(projectId, request(proposal(List.of(classifier("c1", "Pedido")),
                List.of(), List.of(), List.of()))).items().getFirst();

        assertEquals(CONFLICT, item.suggestedAction());
        assertEquals("AMBIGUOUS_CLASSIFIER_MATCH", item.reasonCode());
        assertEquals(2, item.candidates().size());
    }

    @Test
    void duplicateAttributeSignatureSuggestsSkip() {
        UmlClassifier existing = existingClassifier("Producto", ClassifierKind.CLASS);
        when(classifiers.findByElementModelIdOrderByElementNameAsc(modelId)).thenReturn(List.of(existing));
        UmlProperty property = primitiveProperty("codigo", PrimitiveType.STRING, 1, 1);
        when(properties.findByOwnerClassifierElementIdOrderByElementNameAscElementIdAsc(existing.getElementId()))
                .thenReturn(List.of(property));
        DetectedAttribute attribute = new DetectedAttribute("a1", "c1", "codigo", "PRIVATE",
                new DetectedType("PRIMITIVE", "STRING"), 1, 1, false, true, .9);

        var item = service.preview(projectId, request(proposal(List.of(classifier("c1", "Producto")),
                List.of(attribute), List.of(), List.of()))).items().get(1);

        assertEquals(SKIP, item.suggestedAction());
        assertEquals("DUPLICATE_ATTRIBUTE_SIGNATURE", item.reasonCode());
    }

    @Test
    void duplicateOperationUsesOrderedParameterAndReturnSignature() {
        UmlClassifier existing = existingClassifier("Servicio", ClassifierKind.CLASS);
        when(classifiers.findByElementModelIdOrderByElementNameAsc(modelId)).thenReturn(List.of(existing));
        UmlOperation operation = operation("buscar");
        when(operations.findByOwnerClassifierElementIdOrderByElementNameAscElementIdAsc(existing.getElementId()))
                .thenReturn(List.of(operation));
        UmlParameter argument = parameter(ParameterDirection.IN, PrimitiveType.STRING, 0);
        UmlParameter returned = parameter(ParameterDirection.RETURN, PrimitiveType.INTEGER, 1);
        when(parameters.findByOperationElementIdOrderByPositionAsc(operation.getElementId()))
                .thenReturn(List.of(argument, returned));
        DetectedOperation detected = new DetectedOperation("o1", "c1", "buscar", "PUBLIC",
                List.of(new DetectedParameter("p1", "texto", "IN",
                        new DetectedType("PRIMITIVE", "STRING"), 0, 1, 1, false, true, .9)),
                new DetectedType("PRIMITIVE", "INTEGER"), .9);

        var item = service.preview(projectId, request(proposal(List.of(classifier("c1", "Servicio")),
                List.of(), List.of(detected), List.of()))).items().get(1);

        assertEquals(SKIP, item.suggestedAction());
        assertEquals("DUPLICATE_OPERATION_SIGNATURE", item.reasonCode());
    }

    @Test
    void duplicateGeneralizationSuggestsSkip() {
        UmlClassifier child = existingClassifier("Cliente", ClassifierKind.CLASS);
        UmlClassifier parent = existingClassifier("Usuario", ClassifierKind.CLASS);
        when(classifiers.findByElementModelIdOrderByElementNameAsc(modelId)).thenReturn(List.of(child, parent));
        UmlGeneralization relation = mock(UmlGeneralization.class);
        when(relation.getElementId()).thenReturn(UUID.randomUUID());
        when(relation.getSpecificClassifier()).thenReturn(child);
        when(relation.getGeneralClassifier()).thenReturn(parent);
        when(generalizations.findByElementModelIdOrderByElementCreatedAtAsc(modelId))
                .thenReturn(List.of(relation));
        DetectedRelationship detected = relationship("r1", "GENERALIZATION", "child", "parent");

        var item = service.preview(projectId, request(proposal(List.of(
                classifier("child", "Cliente"), classifier("parent", "Usuario")),
                List.of(), List.of(), List.of(detected)))).items().get(2);

        assertEquals(SKIP, item.suggestedAction());
        assertEquals("DUPLICATE_RELATIONSHIP", item.reasonCode());
    }

    @Test
    void duplicateAssociationWithSameSemanticEndsSuggestsSkip() {
        UmlClassifier product = existingClassifier("Producto", ClassifierKind.CLASS);
        UmlClassifier warehouse = existingClassifier("Almacen", ClassifierKind.CLASS);
        when(classifiers.findByElementModelIdOrderByElementNameAsc(modelId))
                .thenReturn(List.of(product, warehouse));
        UmlAssociation association = mock(UmlAssociation.class);
        when(association.getElementId()).thenReturn(UUID.randomUUID());
        when(associations.findByElementModelIdOrderByElementCreatedAtAsc(modelId))
                .thenReturn(List.of(association));
        UmlAssociationEnd productEnd = associationEnd(product, 1, 1);
        UmlAssociationEnd warehouseEnd = associationEnd(warehouse, 0, null);
        when(associationEnds.findByAssociationElementIdOrderByMemberOrderAsc(association.getElementId()))
                .thenReturn(List.of(productEnd, warehouseEnd));
        DetectedRelationship detected = relationship("r1", "ASSOCIATION", "product", "warehouse");

        var item = service.preview(projectId, request(proposal(List.of(
                classifier("product", "Producto"), classifier("warehouse", "Almacen")),
                List.of(), List.of(), List.of(detected)))).items().get(2);

        assertEquals(SKIP, item.suggestedAction());
        assertEquals("DUPLICATE_RELATIONSHIP", item.reasonCode());
    }

    @Test
    void newRelationshipBetweenNewAndExistingClassifierSuggestsCreate() {
        UmlClassifier warehouse = existingClassifier("Almacen", ClassifierKind.CLASS);
        when(classifiers.findByElementModelIdOrderByElementNameAsc(modelId)).thenReturn(List.of(warehouse));
        DetectedRelationship detected = relationship("r1", "ASSOCIATION", "product", "warehouse");

        var result = service.preview(projectId, request(proposal(List.of(
                classifier("product", "Producto"), classifier("warehouse", "Almacen")),
                List.of(), List.of(), List.of(detected))));

        assertEquals(CREATE, result.items().get(2).suggestedAction());
        assertEquals("RELATIONSHIP_USES_NEW_CLASSIFIER", result.items().get(2).reasonCode());
    }

    @Test
    void staleModelRevisionIsControlledConflict() {
        ImageImportException error = assertThrows(ImageImportException.class,
                () -> service.preview(projectId, new ImageImportReconciliationPreviewRequest(
                        modelId, diagramId, 2, 5, emptyProposal())));
        assertEquals(409, error.getStatus().value());
        assertEquals("IMAGE_IMPORT_STALE_MODEL_REVISION", error.getCode());
        verifyNoInteractions(classifiers, properties, operations, associations, nodes);
    }

    @Test
    void viewerIsRejectedBeforeModelDataIsRead() {
        when(authorization.requireWrite(projectId)).thenThrow(new AccessDeniedException("denied"));

        assertThrows(AccessDeniedException.class,
                () -> service.preview(projectId, request(emptyProposal())));

        verifyNoInteractions(models, diagrams, classifiers, properties, operations, associations, nodes);
    }

    @Test
    void modelFromAnotherProjectIsHiddenAsNotFound() {
        Project other = mock(Project.class);
        when(other.getId()).thenReturn(UUID.randomUUID());
        when(model.getProject()).thenReturn(other);

        ImageImportException error = assertThrows(ImageImportException.class,
                () -> service.preview(projectId, request(emptyProposal())));

        assertEquals(404, error.getStatus().value());
        assertEquals("IMAGE_IMPORT_TARGET_MODEL_NOT_FOUND", error.getCode());
        verifyNoInteractions(classifiers, properties, operations, associations, nodes);
    }

    @Test
    void diagramFromAnotherModelIsHiddenAsNotFound() {
        UmlModel other = mock(UmlModel.class);
        when(other.getId()).thenReturn(UUID.randomUUID());
        when(diagram.getModel()).thenReturn(other);

        ImageImportException error = assertThrows(ImageImportException.class,
                () -> service.preview(projectId, request(emptyProposal())));

        assertEquals(404, error.getStatus().value());
        assertEquals("IMAGE_IMPORT_TARGET_DIAGRAM_NOT_FOUND", error.getCode());
    }

    private ImageImportReconciliationPreviewRequest request(DetectedClassDiagram proposal) {
        return new ImageImportReconciliationPreviewRequest(modelId, diagramId, 3, 5, proposal);
    }

    private DetectedClassDiagram emptyProposal() {
        return proposal(List.of(), List.of(), List.of(), List.of());
    }

    private DetectedClassDiagram proposal(List<DetectedClassifier> detectedClassifiers,
            List<DetectedAttribute> detectedAttributes, List<DetectedOperation> detectedOperations,
            List<DetectedRelationship> detectedRelationships) {
        return new DetectedClassDiagram("Import preview", detectedClassifiers, detectedAttributes,
                detectedOperations, detectedRelationships, List.of(), .9);
    }

    private DetectedClassifier classifier(String id, String name) {
        return new DetectedClassifier(id, name, "CLASS", "PUBLIC", .9);
    }

    private DetectedRelationship relationship(String id, String type, String source, String target) {
        DetectedEnd first = new DetectedEnd(source, "", "PUBLIC", 1, 1, false, true, false);
        DetectedEnd second = new DetectedEnd(target, "", "PUBLIC", 0, null, true, true, false);
        return new DetectedRelationship(id, type, "", first, second, .9);
    }

    private UmlClassifier existingClassifier(String name, ClassifierKind kind) {
        UmlClassifier classifier = mock(UmlClassifier.class);
        UmlElement element = mock(UmlElement.class);
        UUID id = UUID.randomUUID();
        when(classifier.getElementId()).thenReturn(id);
        when(classifier.getElement()).thenReturn(element);
        when(classifier.getKind()).thenReturn(kind);
        when(classifier.getUmlPackage()).thenReturn(null);
        when(element.getId()).thenReturn(id);
        when(element.getName()).thenReturn(name);
        return classifier;
    }

    private UmlProperty primitiveProperty(String name, PrimitiveType type, int lower, Integer upper) {
        UmlProperty property = mock(UmlProperty.class);
        UmlElement element = mock(UmlElement.class);
        UUID id = UUID.randomUUID();
        when(property.getElementId()).thenReturn(id);
        when(property.getElement()).thenReturn(element);
        when(property.getPrimitiveType()).thenReturn(type);
        when(property.getLowerBound()).thenReturn(lower);
        when(property.getUpperBound()).thenReturn(upper);
        when(element.getId()).thenReturn(id);
        when(element.getName()).thenReturn(name);
        return property;
    }

    private UmlOperation operation(String name) {
        UmlOperation operation = mock(UmlOperation.class);
        UmlElement element = mock(UmlElement.class);
        UUID id = UUID.randomUUID();
        when(operation.getElementId()).thenReturn(id);
        when(operation.getElement()).thenReturn(element);
        when(element.getName()).thenReturn(name);
        return operation;
    }

    private UmlParameter parameter(ParameterDirection direction, PrimitiveType type, int position) {
        UmlParameter parameter = mock(UmlParameter.class);
        when(parameter.getDirection()).thenReturn(direction);
        when(parameter.getPrimitiveType()).thenReturn(type);
        when(parameter.getPosition()).thenReturn(position);
        return parameter;
    }

    private UmlAssociationEnd associationEnd(UmlClassifier type, int lower, Integer upper) {
        UmlAssociationEnd end = mock(UmlAssociationEnd.class);
        UmlProperty property = mock(UmlProperty.class);
        UmlElement element = mock(UmlElement.class);
        when(end.getProperty()).thenReturn(property);
        when(end.getAggregation()).thenReturn(AggregationKind.NONE);
        when(property.getTypeClassifier()).thenReturn(type);
        when(property.getElement()).thenReturn(element);
        when(property.getLowerBound()).thenReturn(lower);
        when(property.getUpperBound()).thenReturn(upper);
        when(element.getName()).thenReturn("");
        return end;
    }
}
