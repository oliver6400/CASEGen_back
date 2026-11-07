package com.casegen.ai.proposal.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.casegen.ai.proposal.validation.AiProposalValidationErrorCode;
import com.casegen.ai.proposal.validation.AiProposalValidationException;
import com.casegen.modeling.domain.ClassifierKind;
import com.casegen.modeling.domain.Diagram;
import com.casegen.modeling.domain.DiagramNode;
import com.casegen.modeling.domain.UmlClassifier;
import com.casegen.modeling.domain.UmlElement;
import com.casegen.modeling.domain.UmlGeneralization;
import com.casegen.modeling.domain.UmlModel;
import com.casegen.modeling.repository.DiagramEdgeRepository;
import com.casegen.modeling.repository.DiagramNodeRepository;
import com.casegen.modeling.repository.DiagramRepository;
import com.casegen.modeling.repository.UmlAssociationEndRepository;
import com.casegen.modeling.repository.UmlAssociationRepository;
import com.casegen.modeling.repository.UmlClassifierRepository;
import com.casegen.modeling.repository.UmlGeneralizationRepository;
import com.casegen.modeling.repository.UmlModelRepository;
import com.casegen.modeling.repository.UmlOperationRepository;
import com.casegen.modeling.repository.UmlPackageRepository;
import com.casegen.modeling.repository.UmlPropertyRepository;
import com.casegen.modeling.repository.UmlRealizationRepository;
import com.casegen.project.domain.Project;
import com.casegen.project.repository.ProjectRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiProposalContextBuilderTest {
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final UmlModelRepository models = mock(UmlModelRepository.class);
    private final DiagramRepository diagrams = mock(DiagramRepository.class);
    private final DiagramNodeRepository nodes = mock(DiagramNodeRepository.class);
    private final DiagramEdgeRepository edges = mock(DiagramEdgeRepository.class);
    private final UmlPackageRepository packages = mock(UmlPackageRepository.class);
    private final UmlClassifierRepository classifiers = mock(UmlClassifierRepository.class);
    private final UmlPropertyRepository properties = mock(UmlPropertyRepository.class);
    private final UmlOperationRepository operations = mock(UmlOperationRepository.class);
    private final UmlAssociationRepository associations = mock(UmlAssociationRepository.class);
    private final UmlAssociationEndRepository associationEnds = mock(UmlAssociationEndRepository.class);
    private final UmlGeneralizationRepository generalizations = mock(UmlGeneralizationRepository.class);
    private final UmlRealizationRepository realizations = mock(UmlRealizationRepository.class);
    private final AiProposalContextBuilder builder = new AiProposalContextBuilder(projects, models,
            diagrams, nodes, edges, packages, classifiers, properties, operations, associations,
            associationEnds, generalizations, realizations);
    private UUID projectId;
    private UUID modelId;
    private UmlModel model;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        modelId = UUID.randomUUID();
        Project project = mock(Project.class);
        when(project.getId()).thenReturn(projectId);
        model = mock(UmlModel.class);
        when(model.getId()).thenReturn(modelId);
        when(model.getProject()).thenReturn(project);
        when(model.getRevision()).thenReturn(12L);
        when(projects.existsById(projectId)).thenReturn(true);
        when(models.findById(modelId)).thenReturn(Optional.of(model));
        when(packages.findByElementModelIdOrderByElementNameAsc(modelId)).thenReturn(List.of());
        when(classifiers.findByElementModelIdOrderByElementNameAsc(modelId)).thenReturn(List.of());
        when(associations.findByElementModelIdOrderByElementCreatedAtAsc(modelId)).thenReturn(List.of());
        when(generalizations.findByElementModelIdOrderByElementCreatedAtAsc(modelId)).thenReturn(List.of());
        when(realizations.findByElementModelIdOrderByElementCreatedAtAsc(modelId)).thenReturn(List.of());
    }

    @Test
    void capturesModelRevisionAndRealClassifierAndRelationshipIds() {
        UUID childId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UmlClassifier child = classifier(childId, "Child", ClassifierKind.CLASS);
        UmlClassifier parent = classifier(parentId, "Parent", ClassifierKind.CLASS);
        when(classifiers.findByElementModelIdOrderByElementNameAsc(modelId))
                .thenReturn(List.of(child, parent));
        when(properties.findByOwnerClassifierElementIdOrderByElementNameAscElementIdAsc(childId))
                .thenReturn(List.of());
        when(properties.findByOwnerClassifierElementIdOrderByElementNameAscElementIdAsc(parentId))
                .thenReturn(List.of());
        when(operations.findByOwnerClassifierElementIdOrderByElementNameAscElementIdAsc(childId))
                .thenReturn(List.of());
        when(operations.findByOwnerClassifierElementIdOrderByElementNameAscElementIdAsc(parentId))
                .thenReturn(List.of());
        UmlGeneralization relation = mock(UmlGeneralization.class);
        when(relation.getElementId()).thenReturn(UUID.randomUUID());
        when(relation.getSpecificClassifier()).thenReturn(child);
        when(relation.getGeneralClassifier()).thenReturn(parent);
        when(generalizations.findByElementModelIdOrderByElementCreatedAtAsc(modelId))
                .thenReturn(List.of(relation));

        AiProposalContext result = builder.build(projectId, modelId, null);

        assertThat(result.modelRevision()).isEqualTo(12);
        assertThat(result.classifiers()).extracting(AiProposalContext.ClassifierEntry::id)
                .containsExactly(childId, parentId);
        assertThat(result.relationships()).singleElement().satisfies(entry -> {
            assertThat(entry.id()).isEqualTo(relation.getElementId());
            assertThat(entry.firstClassifierId()).isEqualTo(childId);
            assertThat(entry.secondClassifierId()).isEqualTo(parentId);
        });
    }

    @Test
    void diagramScopeMarksVisibleElementsWithoutReadingGeometry() {
        UUID diagramId = UUID.randomUUID();
        UUID classifierId = UUID.randomUUID();
        UmlClassifier classifier = classifier(classifierId, "Visible", ClassifierKind.CLASS);
        when(classifiers.findByElementModelIdOrderByElementNameAsc(modelId)).thenReturn(List.of(classifier));
        when(properties.findByOwnerClassifierElementIdOrderByElementNameAscElementIdAsc(classifierId))
                .thenReturn(List.of());
        when(operations.findByOwnerClassifierElementIdOrderByElementNameAscElementIdAsc(classifierId))
                .thenReturn(List.of());
        Diagram diagram = mock(Diagram.class);
        when(diagram.getModel()).thenReturn(model);
        when(diagram.getRevision()).thenReturn(4L);
        when(diagrams.findById(diagramId)).thenReturn(Optional.of(diagram));
        DiagramNode node = mock(DiagramNode.class);
        UmlElement classifierElement = classifier.getElement();
        when(node.getUmlElement()).thenReturn(classifierElement);
        when(nodes.findByDiagramIdOrderByZIndexAscCreatedAtAsc(diagramId)).thenReturn(List.of(node));
        when(edges.findByDiagramIdOrderByCreatedAtAsc(diagramId)).thenReturn(List.of());

        AiProposalContext result = builder.build(projectId, modelId, diagramId);

        assertThat(result.diagramRevision()).isEqualTo(4);
        assertThat(result.classifiers()).singleElement()
                .extracting(AiProposalContext.ClassifierEntry::visible).isEqualTo(true);
        verify(nodes).findByDiagramIdOrderByZIndexAscCreatedAtAsc(diagramId);
    }

    @Test
    void rejectsModelFromAnotherProject() {
        when(model.getProject().getId()).thenReturn(UUID.randomUUID());
        rejects(() -> builder.build(projectId, modelId, null),
                AiProposalValidationErrorCode.ELEMENT_OUTSIDE_MODEL);
    }

    @Test
    void rejectsDiagramFromAnotherModel() {
        UUID diagramId = UUID.randomUUID();
        UmlModel other = mock(UmlModel.class);
        when(other.getId()).thenReturn(UUID.randomUUID());
        Diagram diagram = mock(Diagram.class);
        when(diagram.getModel()).thenReturn(other);
        when(diagrams.findById(diagramId)).thenReturn(Optional.of(diagram));
        rejects(() -> builder.build(projectId, modelId, diagramId),
                AiProposalValidationErrorCode.ELEMENT_OUTSIDE_MODEL);
    }

    private UmlClassifier classifier(UUID id, String name, ClassifierKind kind) {
        UmlElement element = mock(UmlElement.class);
        when(element.getId()).thenReturn(id);
        when(element.getName()).thenReturn(name);
        UmlClassifier classifier = mock(UmlClassifier.class);
        when(classifier.getElementId()).thenReturn(id);
        when(classifier.getElement()).thenReturn(element);
        when(classifier.getKind()).thenReturn(kind);
        return classifier;
    }

    private void rejects(org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
            AiProposalValidationErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(AiProposalValidationException.class)
                .extracting(error -> ((AiProposalValidationException) error).getCode())
                .isEqualTo(code);
    }
}
