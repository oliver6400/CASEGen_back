package com.casegen.interoperability.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.casegen.auth.domain.User;
import com.casegen.interoperability.xmi.ImportedClassDiagramCreator;
import com.casegen.modeling.command.CreateUmlAssociationCommand;
import com.casegen.modeling.command.CreateUmlGeneralizationCommand;
import com.casegen.modeling.command.CreateUmlPropertyCommand;
import com.casegen.modeling.command.CreateUmlRealizationCommand;
import com.casegen.modeling.domain.AggregationKind;
import com.casegen.modeling.domain.Visibility;
import com.casegen.modeling.dto.UmlAssociationResponse;
import com.casegen.modeling.dto.UmlClassifierResponse;
import com.casegen.modeling.dto.UmlGeneralizationResponse;
import com.casegen.modeling.dto.UmlModelResponse;
import com.casegen.modeling.dto.UmlPropertyResponse;
import com.casegen.modeling.dto.UmlRealizationResponse;
import com.casegen.modeling.service.UmlModelService;
import com.casegen.modeling.service.UmlRelationshipService;
import com.casegen.project.service.ProjectAuthorizationService;
import com.casegen.validation.dto.ValidationResponse;
import com.casegen.validation.service.ValidationService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.mockito.ArgumentCaptor;

class ImageImportServiceTest {
    private final ImageAnalysisProvider analyzer = mock(ImageAnalysisProvider.class);
    private final ProjectAuthorizationService authorization = mock(ProjectAuthorizationService.class);
    private final UmlModelService models = mock(UmlModelService.class);
    private final UmlRelationshipService relationships = mock(UmlRelationshipService.class);
    private final ImportedClassDiagramCreator diagrams = mock(ImportedClassDiagramCreator.class);
    private final ValidationService validation = mock(ValidationService.class);
    private ImageImportService service;

    @BeforeEach
    void setUp() {
        service = new ImageImportService(analyzer, authorization, models, relationships,
                diagrams, validation, 10 * 1024 * 1024);
    }

    @Test
    void validPngProducesEditableProposalAndDoesNotPersist() {
        UUID projectId = UUID.randomUUID();
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        DetectedClassDiagram expected = proposal(0.91);
        when(analyzer.analyze(png, "image/png", "uml.png")).thenReturn(expected);

        assertEquals(expected, service.analyze(projectId, png, "image/png", "uml.png"));
        verify(models, never()).createModel(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void lowConfidenceAddsClearWarning() {
        UUID projectId = UUID.randomUUID();
        byte[] jpeg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01};
        when(analyzer.analyze(jpeg, "image/jpeg", "blur.jpg")).thenReturn(proposal(0.3));

        var result = service.analyze(projectId, jpeg, "image/jpeg", "blur.jpg");

        assertEquals("LOW_CONFIDENCE", result.warnings().getFirst().code());
    }

    @Test
    void analysisShowsSemanticVisibilityDefaultsInPreview() {
        UUID projectId = UUID.randomUUID();
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        var detected = new DetectedClassDiagram("Defaults", List.of(
                new DetectedClassDiagram.DetectedClassifier(
                        "c1", "Customer", "CLASS", "NOT_APPLICABLE", .9)),
                List.of(attribute("a1", "c1", "id", "INTEGER", "UNKNOWN")),
                List.of(), List.of(), List.of(), .9);
        when(analyzer.analyze(png, "image/png", "defaults.png")).thenReturn(detected);

        DetectedClassDiagram result = service.analyze(projectId, png, "image/png", "defaults.png");

        assertEquals("PUBLIC", result.detectedClassifiers().getFirst().visibility());
        assertEquals("PRIVATE", result.detectedAttributes().getFirst().visibility());
    }

    @Test
    void analysisNormalizesInterfaceStereotypePrimitiveTypesAndSuspiciousHeaders() {
        UUID projectId = UUID.randomUUID();
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        var detected = new DetectedClassDiagram(" Model ", List.of(
                new DetectedClassDiagram.DetectedClassifier(
                        "c1", " <<interface>> Payable ", "CLASS", "PUBLIC", .9),
                new DetectedClassDiagram.DetectedClassifier(
                        "c2", " amount : Real ", "CLASS", "PUBLIC", .45)),
                List.of(attribute("a1", "c1", " value ", "string", "PRIVATE")),
                List.of(), List.of(), List.of(), .9);
        when(analyzer.provider()).thenReturn("GEMINI");
        when(analyzer.analyze(png, "image/png", "model.png")).thenReturn(detected);

        DetectedClassDiagram result = service.analyze(projectId, png, "image/png", "model.png");

        assertEquals("Model", result.suggestedModelName());
        assertEquals("Payable", result.detectedClassifiers().getFirst().name());
        assertEquals("INTERFACE", result.detectedClassifiers().getFirst().kind());
        assertEquals("STRING", result.detectedAttributes().getFirst().type().value());
        assertEquals("SUSPICIOUS_CLASSIFIER_HEADER", result.warnings().getFirst().code());
    }

    @Test
    void invalidDetectedVisibilityReturnsSpecificFieldErrorBeforePersistence() {
        UUID projectId = UUID.randomUUID();
        User actor = mock(User.class);
        when(actor.getId()).thenReturn(UUID.randomUUID());
        when(authorization.requireWrite(projectId)).thenReturn(actor);
        var invalid = new DetectedClassDiagram("Invalid", List.of(
                new DetectedClassDiagram.DetectedClassifier(
                        "c1", "Customer", "CLASS", "INTERNAL", .9)),
                List.of(), List.of(), List.of(), List.of(), .9);

        ImageImportException exception = assertThrows(ImageImportException.class,
                () -> service.confirm(projectId, invalid));

        assertEquals("UNRESOLVED_DETECTED_FIELD", exception.getCode());
        assertEquals(true, exception.getMessage().contains("element=c1"));
        assertEquals(true, exception.getMessage().contains("field=visibility"));
        assertEquals(true, exception.getMessage().contains("received=INTERNAL"));
        verify(models, never()).createModel(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void invalidImageContentIsRejectedBeforeProviderCall() {
        ImageImportException exception = assertThrows(ImageImportException.class,
                () -> service.analyze(UUID.randomUUID(), "not an image".getBytes(), "image/png", "fake.png"));
        assertEquals("UNSUPPORTED_IMAGE", exception.getCode());
        verify(analyzer, never()).analyze(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void viewerCannotAnalyze() {
        UUID projectId = UUID.randomUUID();
        when(authorization.requireWrite(projectId)).thenThrow(new AccessDeniedException("denied"));
        assertThrows(AccessDeniedException.class, () -> service.analyze(projectId,
                new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0, 0, 0, 0}, "image/png", "uml.png"));
        verify(analyzer, never()).analyze(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void confirmedCustomerOrderCreatesSemanticElementsDiagramAndValidation() {
        UUID projectId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID associationId = UUID.randomUUID();
        User actor = mock(User.class);
        when(actor.getId()).thenReturn(UUID.randomUUID());
        when(authorization.requireWrite(projectId)).thenReturn(actor);
        UmlModelResponse model = mock(UmlModelResponse.class);
        when(model.id()).thenReturn(modelId);
        when(models.createModel(org.mockito.ArgumentMatchers.eq(projectId), org.mockito.ArgumentMatchers.any()))
                .thenReturn(model);
        UmlClassifierResponse customer = mock(UmlClassifierResponse.class);
        UmlClassifierResponse order = mock(UmlClassifierResponse.class);
        when(customer.id()).thenReturn(customerId);
        when(order.id()).thenReturn(orderId);
        when(models.createClassifier(org.mockito.ArgumentMatchers.eq(modelId), org.mockito.ArgumentMatchers.any()))
                .thenReturn(customer, order);
        UmlPropertyResponse property = mock(UmlPropertyResponse.class);
        when(property.id()).thenReturn(UUID.randomUUID());
        when(models.createProperty(org.mockito.ArgumentMatchers.eq(modelId), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(property);
        UmlAssociationResponse association = mock(UmlAssociationResponse.class);
        when(association.id()).thenReturn(associationId);
        when(relationships.createAssociation(org.mockito.ArgumentMatchers.eq(modelId),
                org.mockito.ArgumentMatchers.any())).thenReturn(association);
        when(diagrams.create(org.mockito.ArgumentMatchers.eq(modelId), org.mockito.ArgumentMatchers.eq("Ventas"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ImportedClassDiagramCreator.CreationResult(
                        UUID.randomUUID(), "Ventas - Importado", 2, 1, true));
        when(validation.validate(modelId)).thenReturn(new ValidationResponse(
                UUID.randomUUID(), modelId, 7, "VALID", true, null, null, List.of()));

        var classifiers = List.of(
                new DetectedClassDiagram.DetectedClassifier("c1", "Cliente", "CLASS", "PUBLIC", .98),
                new DetectedClassDiagram.DetectedClassifier("c2", "Pedido", "CLASS", "PUBLIC", .98));
        var attributes = List.of(
                attribute("a1", "c1", "id", "INTEGER", "UNKNOWN"), attribute("a2", "c1", "nombre", "STRING"),
                attribute("a3", "c2", "id", "INTEGER"), attribute("a4", "c2", "total", "REAL"));
        var relation = new DetectedClassDiagram.DetectedRelationship("r1", "ASSOCIATION", "",
                end("c1", 1, 1, false), end("c2", 0, null, true), .97);
        var result = service.confirm(projectId, new DetectedClassDiagram(
                "Ventas", classifiers, attributes, List.of(), List.of(relation), List.of(), .97));

        assertEquals("VALID", result.validation().status());
        assertEquals(2, result.diagram().nodeCount());
        verify(models, org.mockito.Mockito.times(4)).createProperty(
                org.mockito.ArgumentMatchers.eq(modelId), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(CreateUmlPropertyCommand.class));
        ArgumentCaptor<CreateUmlPropertyCommand> properties = ArgumentCaptor.forClass(CreateUmlPropertyCommand.class);
        verify(models, org.mockito.Mockito.times(4)).createProperty(
                org.mockito.ArgumentMatchers.eq(modelId), org.mockito.ArgumentMatchers.any(), properties.capture());
        assertEquals(Visibility.PRIVATE, properties.getAllValues().getFirst().visibility());
        ArgumentCaptor<CreateUmlAssociationCommand> command = ArgumentCaptor.forClass(CreateUmlAssociationCommand.class);
        verify(relationships).createAssociation(org.mockito.ArgumentMatchers.eq(modelId), command.capture());
        assertEquals(1, command.getValue().firstEnd().lowerBound());
        assertEquals(0, command.getValue().secondEnd().lowerBound());
        assertEquals(null, command.getValue().secondEnd().upperBound());
        assertEquals(AggregationKind.NONE, command.getValue().firstEnd().aggregation());
        verify(validation).validate(modelId);
    }

    @Test
    void confirmedRelationshipKindsAreMappedToExistingSemanticCommands() {
        UUID projectId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        User actor = mock(User.class);
        when(actor.getId()).thenReturn(UUID.randomUUID());
        when(authorization.requireWrite(projectId)).thenReturn(actor);
        UmlModelResponse model = mock(UmlModelResponse.class);
        when(model.id()).thenReturn(modelId);
        when(models.createModel(org.mockito.ArgumentMatchers.eq(projectId), org.mockito.ArgumentMatchers.any())).thenReturn(model);
        UUID wholeId = UUID.randomUUID(), partId = UUID.randomUUID(), parentId = UUID.randomUUID(), interfaceId = UUID.randomUUID();
        UmlClassifierResponse whole = classifier(wholeId), part = classifier(partId), parent = classifier(parentId), contract = classifier(interfaceId);
        when(models.createClassifier(org.mockito.ArgumentMatchers.eq(modelId), org.mockito.ArgumentMatchers.any()))
                .thenReturn(whole, part, parent, contract);
        UmlAssociationResponse association = mock(UmlAssociationResponse.class);
        when(association.id()).thenReturn(UUID.randomUUID());
        when(relationships.createAssociation(org.mockito.ArgumentMatchers.eq(modelId), org.mockito.ArgumentMatchers.any()))
                .thenReturn(association);
        UmlGeneralizationResponse generalization = mock(UmlGeneralizationResponse.class);
        when(generalization.id()).thenReturn(UUID.randomUUID());
        when(relationships.createGeneralization(org.mockito.ArgumentMatchers.eq(modelId), org.mockito.ArgumentMatchers.any()))
                .thenReturn(generalization);
        UmlRealizationResponse realization = mock(UmlRealizationResponse.class);
        when(realization.id()).thenReturn(UUID.randomUUID());
        when(relationships.createRealization(org.mockito.ArgumentMatchers.eq(modelId), org.mockito.ArgumentMatchers.any()))
                .thenReturn(realization);
        when(diagrams.create(org.mockito.ArgumentMatchers.eq(modelId), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ImportedClassDiagramCreator.CreationResult(UUID.randomUUID(), "Relations - Importado", 4, 4, true));
        when(validation.validate(modelId)).thenReturn(new ValidationResponse(
                UUID.randomUUID(), modelId, 8, "VALID", true, null, null, List.of()));
        var classifierProposal = List.of(
                new DetectedClassDiagram.DetectedClassifier("whole", "Whole", "CLASS", "PUBLIC", 1),
                new DetectedClassDiagram.DetectedClassifier("part", "Part", "CLASS", "PUBLIC", 1),
                new DetectedClassDiagram.DetectedClassifier("parent", "Parent", "CLASS", "PUBLIC", 1),
                new DetectedClassDiagram.DetectedClassifier("contract", "Contract", "INTERFACE", "PUBLIC", 1));
        var detectedRelationships = List.of(
                relationship("a", "AGGREGATION", "whole", "part"),
                relationship("c", "COMPOSITION", "whole", "part"),
                nonAssociationRelationship("g", "GENERALIZATION", "part", "parent"),
                nonAssociationRelationship("r", "REALIZATION", "whole", "contract"));

        service.confirm(projectId, new DetectedClassDiagram("Relations", classifierProposal,
                List.of(), List.of(), detectedRelationships, List.of(), 1));

        ArgumentCaptor<CreateUmlAssociationCommand> associations = ArgumentCaptor.forClass(CreateUmlAssociationCommand.class);
        verify(relationships, org.mockito.Mockito.times(2)).createAssociation(
                org.mockito.ArgumentMatchers.eq(modelId), associations.capture());
        assertEquals(AggregationKind.SHARED, associations.getAllValues().get(0).firstEnd().aggregation());
        assertEquals(AggregationKind.COMPOSITE, associations.getAllValues().get(1).firstEnd().aggregation());
        assertEquals(Visibility.PUBLIC, associations.getAllValues().get(0).firstEnd().visibility());
        ArgumentCaptor<CreateUmlGeneralizationCommand> general = ArgumentCaptor.forClass(CreateUmlGeneralizationCommand.class);
        verify(relationships).createGeneralization(org.mockito.ArgumentMatchers.eq(modelId), general.capture());
        assertEquals(partId, general.getValue().specificClassifierId());
        assertEquals(parentId, general.getValue().generalClassifierId());
        ArgumentCaptor<CreateUmlRealizationCommand> real = ArgumentCaptor.forClass(CreateUmlRealizationCommand.class);
        verify(relationships).createRealization(org.mockito.ArgumentMatchers.eq(modelId), real.capture());
        assertEquals(wholeId, real.getValue().clientClassifierId());
        assertEquals(interfaceId, real.getValue().supplierClassifierId());
    }

    private UmlClassifierResponse classifier(UUID id) {
        UmlClassifierResponse response = mock(UmlClassifierResponse.class);
        when(response.id()).thenReturn(id);
        return response;
    }

    private DetectedClassDiagram.DetectedRelationship relationship(String id, String type, String source, String target) {
        return new DetectedClassDiagram.DetectedRelationship(id, type, "", end(source, 1, 1, false, "UNKNOWN"),
                end(target, 0, null, true), 1);
    }

    private DetectedClassDiagram.DetectedRelationship nonAssociationRelationship(
            String id, String type, String source, String target) {
        return new DetectedClassDiagram.DetectedRelationship(id, type, "",
                end(source, 1, 1, false, "NOT_APPLICABLE"),
                end(target, 1, 1, false, "NOT_APPLICABLE"), 1);
    }

    private DetectedClassDiagram.DetectedAttribute attribute(String id, String owner, String name, String type) {
        return attribute(id, owner, name, type, "PRIVATE");
    }

    private DetectedClassDiagram.DetectedAttribute attribute(
            String id, String owner, String name, String type, String visibility) {
        return new DetectedClassDiagram.DetectedAttribute(id, owner, name, visibility,
                new DetectedClassDiagram.DetectedType("PRIMITIVE", type), 1, 1, false, true, .96);
    }

    private DetectedClassDiagram.DetectedEnd end(String classifier, int lower, Integer upper, boolean unlimited) {
        return end(classifier, lower, upper, unlimited, "PUBLIC");
    }

    private DetectedClassDiagram.DetectedEnd end(
            String classifier, int lower, Integer upper, boolean unlimited, String visibility) {
        return new DetectedClassDiagram.DetectedEnd(classifier, "", visibility, lower, upper,
                unlimited, true, false);
    }

    private DetectedClassDiagram proposal(double confidence) {
        return new DetectedClassDiagram("Ventas", List.of(
                new DetectedClassDiagram.DetectedClassifier("c1", "Cliente", "CLASS", "PUBLIC", confidence)),
                List.of(), List.of(), List.of(), List.of(), confidence);
    }
}
