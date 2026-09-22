package com.casegen.modeling.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.casegen.modeling.command.CreateUmlAssociationCommand;
import com.casegen.modeling.command.CreateUmlClassifierCommand;
import com.casegen.modeling.command.CreateUmlModelCommand;
import com.casegen.modeling.command.UmlAssociationEndCommand;
import com.casegen.modeling.domain.AggregationKind;
import com.casegen.modeling.domain.AssociationEndOwnership;
import com.casegen.modeling.domain.ClassifierKind;
import com.casegen.modeling.domain.DiagramType;
import com.casegen.modeling.domain.DiagramSide;
import com.casegen.modeling.domain.Visibility;
import com.casegen.modeling.dto.AddDiagramEdgeRequest;
import com.casegen.modeling.dto.AddDiagramNodeRequest;
import com.casegen.modeling.dto.CreateDiagramRequest;
import com.casegen.modeling.dto.DiagramNodeResponse;
import com.casegen.modeling.dto.DiagramResponse;
import com.casegen.modeling.dto.UpdateDiagramNodeGeometryRequest;
import com.casegen.modeling.dto.UpdateDiagramEdgeLayoutRequest;
import com.casegen.modeling.dto.DiagramWaypoint;
import com.casegen.modeling.dto.UmlClassifierResponse;
import com.casegen.modeling.dto.UmlModelResponse;
import com.casegen.modeling.exception.SemanticErrorCode;
import com.casegen.modeling.exception.SemanticException;
import com.casegen.modeling.repository.UmlElementRepository;
import com.casegen.modeling.service.DiagramService;
import com.casegen.modeling.service.DiagramRepresentationCleanupService;
import com.casegen.modeling.service.UmlModelService;
import com.casegen.modeling.service.UmlRelationshipService;
import com.casegen.project.domain.Project;
import com.casegen.project.repository.ProjectRepository;
import com.casegen.project.service.ProjectAuthorizationService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestConstructor;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({UmlModelService.class, UmlRelationshipService.class, DiagramService.class,
        DiagramRepresentationCleanupService.class,
        ClassDiagramPersistenceIntegrationTest.AuthorizationTestConfig.class})
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = "jdbc:postgresql:.*")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ClassDiagramPersistenceIntegrationTest {

    @TestConfiguration
    static class AuthorizationTestConfig {

        @Bean
        ProjectAuthorizationService projectAuthorizationService() {
            return org.mockito.Mockito.mock(ProjectAuthorizationService.class);
        }

        @Bean
        com.casegen.collaboration.RealtimeEventPublisher realtimeEventPublisher() {
            return org.mockito.Mockito.mock(com.casegen.collaboration.RealtimeEventPublisher.class);
        }
    }

    private final DiagramService diagramService;
    private final UmlModelService modelService;
    private final UmlRelationshipService relationshipService;
    private final ProjectRepository projectRepository;
    private final UmlElementRepository elementRepository;

    ClassDiagramPersistenceIntegrationTest(
            DiagramService diagramService,
            UmlModelService modelService,
            UmlRelationshipService relationshipService,
            ProjectRepository projectRepository,
            UmlElementRepository elementRepository) {
        this.diagramService = diagramService;
        this.modelService = modelService;
        this.relationshipService = relationshipService;
        this.projectRepository = projectRepository;
        this.elementRepository = elementRepository;
    }

    @Test
    void createsClassDiagram() {
        Fixture fixture = fixture();

        DiagramResponse diagram = createDiagram(fixture.model(), "Main");

        assertEquals(DiagramType.CLASS, diagram.type());
        assertEquals(0, diagram.revision());
        assertTrue(diagram.nodes().isEmpty());
        assertTrue(diagram.edges().isEmpty());
    }

    @Test
    void addsNodeAndPersistsMovedGeometryWithoutChangingModelRevision() {
        Fixture fixture = fixture();
        DiagramResponse diagram = createDiagram(fixture.model(), "Main");
        long modelRevisionBefore = modelService.getModel(fixture.model().id()).revision();
        DiagramNodeResponse node = addNode(diagram.id(), fixture.first().id(), 40, 60);

        diagramService.updateNodeGeometry(diagram.id(), node.id(),
                new UpdateDiagramNodeGeometryRequest(125.5, 210.25, 340, 220, 3));
        DiagramResponse reloaded = diagramService.getDiagram(diagram.id());

        DiagramNodeResponse moved = reloaded.nodes().getFirst();
        assertEquals(125.5, moved.x());
        assertEquals(210.25, moved.y());
        assertEquals(340, moved.width());
        assertEquals(220, moved.height());
        assertEquals(2, reloaded.revision());
        assertEquals(modelRevisionBefore, modelService.getModel(fixture.model().id()).revision());
    }

    @Test
    void rejectsDuplicateElementInSameDiagram() {
        Fixture fixture = fixture();
        DiagramResponse diagram = createDiagram(fixture.model(), "Main");
        addNode(diagram.id(), fixture.first().id(), 0, 0);

        SemanticException exception = assertThrows(SemanticException.class,
                () -> addNode(diagram.id(), fixture.first().id(), 400, 0));

        assertEquals(SemanticErrorCode.DIAGRAM_ELEMENT_ALREADY_PRESENT, exception.getCode());
        assertEquals(1, diagramService.getDiagram(diagram.id()).revision());
    }

    @Test
    void allowsSameElementInTwoDiagrams() {
        Fixture fixture = fixture();
        DiagramResponse firstDiagram = createDiagram(fixture.model(), "First view");
        DiagramResponse secondDiagram = createDiagram(fixture.model(), "Second view");

        addNode(firstDiagram.id(), fixture.first().id(), 0, 0);
        addNode(secondDiagram.id(), fixture.first().id(), 100, 100);

        assertEquals(1, diagramService.getDiagram(firstDiagram.id()).nodes().size());
        assertEquals(1, diagramService.getDiagram(secondDiagram.id()).nodes().size());
    }

    @Test
    void addsSemanticRelationshipEdgeAndIncrementsRevisionOnce() {
        Fixture fixture = fixture();
        DiagramResponse diagram = createDiagram(fixture.model(), "Main");
        DiagramNodeResponse source = addNode(diagram.id(), fixture.first().id(), 0, 0);
        DiagramNodeResponse target = addNode(diagram.id(), fixture.second().id(), 400, 0);
        var association = relationshipService.createAssociation(fixture.model().id(),
                association(fixture.first().id(), fixture.second().id()));
        long before = diagramService.getDiagram(diagram.id()).revision();

        var edge = diagramService.addEdge(diagram.id(),
                new AddDiagramEdgeRequest(association.id(), source.id(), target.id()));

        assertEquals(DiagramSide.RIGHT, edge.sourceSide());
        assertEquals(DiagramSide.LEFT, edge.targetSide());

        DiagramResponse reloaded = diagramService.getDiagram(diagram.id());
        assertEquals(1, reloaded.edges().size());
        assertEquals(before + 1, reloaded.revision());
    }

    @Test
    void persistsDistributedAnchorsAndOrthogonalWaypoints() {
        Fixture fixture = fixture();
        DiagramResponse diagram = createDiagram(fixture.model(), "Routing");
        DiagramNodeResponse source = addNode(diagram.id(), fixture.first().id(), 0, 0);
        DiagramNodeResponse target = addNode(diagram.id(), fixture.second().id(), 400, 0);
        var association = relationshipService.createAssociation(fixture.model().id(),
                association(fixture.first().id(), fixture.second().id()));
        var edge = diagramService.addEdge(diagram.id(), new AddDiagramEdgeRequest(
                association.id(), source.id(), target.id(), DiagramSide.RIGHT,
                DiagramSide.LEFT, 2, 1, java.util.List.of()));
        long revisionBeforeLayoutUpdate = diagramService.getDiagram(diagram.id()).revision();

        diagramService.updateEdgeLayout(diagram.id(), edge.id(),
                new UpdateDiagramEdgeLayoutRequest(DiagramSide.BOTTOM, DiagramSide.TOP,
                        3, 4, java.util.List.of(
                                new DiagramWaypoint(150, 250),
                                new DiagramWaypoint(450, 250))));

        var reloaded = diagramService.getDiagram(diagram.id());
        var persisted = reloaded.edges().getFirst();
        assertEquals(DiagramSide.BOTTOM, persisted.sourceSide());
        assertEquals(DiagramSide.TOP, persisted.targetSide());
        assertEquals(3, persisted.sourceAnchorIndex());
        assertEquals(4, persisted.targetAnchorIndex());
        assertEquals(java.util.List.of(new DiagramWaypoint(150, 250),
                new DiagramWaypoint(450, 250)), persisted.waypoints());
        assertEquals(revisionBeforeLayoutUpdate + 1, reloaded.revision());
    }

    @Test
    void rejectsCrossModelElementWithoutChangingDiagramRevision() {
        Fixture fixture = fixture();
        Fixture other = fixture();
        DiagramResponse diagram = createDiagram(fixture.model(), "Main");

        SemanticException exception = assertThrows(SemanticException.class,
                () -> addNode(diagram.id(), other.first().id(), 0, 0));

        assertEquals(SemanticErrorCode.DIAGRAM_MODEL_MISMATCH, exception.getCode());
        assertEquals(0, diagramService.getDiagram(diagram.id()).revision());
    }

    @Test
    void removingNodeFromDiagramDoesNotDeleteSemanticElement() {
        Fixture fixture = fixture();
        DiagramResponse diagram = createDiagram(fixture.model(), "Main");
        DiagramNodeResponse node = addNode(diagram.id(), fixture.first().id(), 0, 0);

        diagramService.removeNode(diagram.id(), node.id());

        assertTrue(diagramService.getDiagram(diagram.id()).nodes().isEmpty());
        assertTrue(elementRepository.existsById(fixture.first().id()));
    }

    @Test
    void deletingSemanticElementCleansEveryDiagramRepresentation() {
        Fixture fixture = fixture();
        DiagramResponse firstDiagram = createDiagram(fixture.model(), "First view");
        DiagramResponse secondDiagram = createDiagram(fixture.model(), "Second view");
        addNode(firstDiagram.id(), fixture.first().id(), 0, 0);
        addNode(secondDiagram.id(), fixture.first().id(), 100, 100);
        long firstRevision = diagramService.getDiagram(firstDiagram.id()).revision();
        long secondRevision = diagramService.getDiagram(secondDiagram.id()).revision();

        modelService.deleteClassifier(fixture.model().id(), fixture.first().id());

        assertTrue(diagramService.getDiagram(firstDiagram.id()).nodes().isEmpty());
        assertTrue(diagramService.getDiagram(secondDiagram.id()).nodes().isEmpty());
        assertEquals(firstRevision + 1, diagramService.getDiagram(firstDiagram.id()).revision());
        assertEquals(secondRevision + 1, diagramService.getDiagram(secondDiagram.id()).revision());
    }

    private Fixture fixture() {
        Project project = projectRepository.saveAndFlush(
                new Project(uniqueName("Project"), "Class diagram persistence test"));
        UmlModelResponse model = modelService.createModel(
                project.getId(), new CreateUmlModelCommand(uniqueName("Model"), null));
        return new Fixture(
                model,
                modelService.createClassifier(model.id(), new CreateUmlClassifierCommand(
                        "Customer", null, null, ClassifierKind.CLASS,
                        Visibility.PUBLIC, false, false)),
                modelService.createClassifier(model.id(), new CreateUmlClassifierCommand(
                        "Order", null, null, ClassifierKind.CLASS,
                        Visibility.PUBLIC, false, false)));
    }

    private DiagramResponse createDiagram(UmlModelResponse model, String name) {
        return diagramService.createDiagram(model.id(),
                new CreateDiagramRequest(name, DiagramType.CLASS, null));
    }

    private DiagramNodeResponse addNode(UUID diagramId, UUID elementId, double x, double y) {
        return diagramService.addNode(diagramId,
                new AddDiagramNodeRequest(elementId, x, y, 280, 180, 0));
    }

    private CreateUmlAssociationCommand association(UUID first, UUID second) {
        return new CreateUmlAssociationCommand(null, null, null,
                new UmlAssociationEndCommand(
                        null, first, Visibility.PUBLIC, 1, 1, false,
                        AggregationKind.NONE, AssociationEndOwnership.ASSOCIATION),
                new UmlAssociationEndCommand(
                        null, second, Visibility.PUBLIC, 0, null, false,
                        AggregationKind.NONE, AssociationEndOwnership.ASSOCIATION));
    }

    private String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private record Fixture(
            UmlModelResponse model, UmlClassifierResponse first, UmlClassifierResponse second) {
    }
}
