package com.casegen.ai.context;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.casegen.modeling.domain.ClassifierKind;
import com.casegen.modeling.domain.Diagram;
import com.casegen.modeling.domain.DiagramEdge;
import com.casegen.modeling.domain.DiagramNode;
import com.casegen.modeling.domain.AggregationKind;
import com.casegen.modeling.domain.ParameterDirection;
import com.casegen.modeling.domain.PrimitiveType;
import com.casegen.modeling.domain.TypeKind;
import com.casegen.modeling.domain.UmlClassifier;
import com.casegen.modeling.domain.UmlAssociation;
import com.casegen.modeling.domain.UmlAssociationEnd;
import com.casegen.modeling.domain.UmlElement;
import com.casegen.modeling.domain.UmlGeneralization;
import com.casegen.modeling.domain.UmlModel;
import com.casegen.modeling.domain.UmlOperation;
import com.casegen.modeling.domain.UmlParameter;
import com.casegen.modeling.domain.UmlProperty;
import com.casegen.modeling.repository.DiagramEdgeRepository;
import com.casegen.modeling.repository.DiagramNodeRepository;
import com.casegen.modeling.repository.UmlAssociationEndRepository;
import com.casegen.modeling.repository.UmlAssociationRepository;
import com.casegen.modeling.repository.UmlClassifierRepository;
import com.casegen.modeling.repository.UmlGeneralizationRepository;
import com.casegen.modeling.repository.UmlOperationRepository;
import com.casegen.modeling.repository.UmlParameterRepository;
import com.casegen.modeling.repository.UmlPropertyRepository;
import com.casegen.modeling.repository.UmlRealizationRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UmlContextSummaryBuilderTest {

    private UmlClassifierRepository classifiers;
    private UmlPropertyRepository properties;
    private UmlOperationRepository operations;
    private UmlParameterRepository parameters;
    private UmlGeneralizationRepository generalizations;
    private UmlAssociationRepository associations;
    private UmlAssociationEndRepository associationEnds;
    private DiagramNodeRepository nodes;
    private DiagramEdgeRepository edges;
    private UmlContextSummaryBuilder builder;

    @BeforeEach
    void setUp() {
        classifiers = mock(UmlClassifierRepository.class);
        properties = mock(UmlPropertyRepository.class);
        operations = mock(UmlOperationRepository.class);
        parameters = mock(UmlParameterRepository.class);
        generalizations = mock(UmlGeneralizationRepository.class);
        UmlRealizationRepository realizations = mock(UmlRealizationRepository.class);
        associations = mock(UmlAssociationRepository.class);
        associationEnds = mock(UmlAssociationEndRepository.class);
        nodes = mock(DiagramNodeRepository.class);
        edges = mock(DiagramEdgeRepository.class);
        when(classifiers.findByElementModelIdOrderByElementNameAsc(any())).thenReturn(List.of());
        when(generalizations.findByElementModelIdOrderByElementCreatedAtAsc(any())).thenReturn(List.of());
        when(realizations.findByElementModelIdOrderByElementCreatedAtAsc(any())).thenReturn(List.of());
        when(associations.findByElementModelIdOrderByElementCreatedAtAsc(any())).thenReturn(List.of());
        builder = new UmlContextSummaryBuilder(classifiers, properties, operations, parameters,
                generalizations, realizations, associations, associationEnds, nodes, edges);
    }

    @Test
    void deterministicSummaryContainsAttributesOperationsRelationsAndMultiplicities() {
        UmlModel model = model();
        UmlClassifier user = classifier("Usuario", ClassifierKind.CLASS);
        UmlClassifier client = classifier("Cliente", ClassifierKind.CLASS);
        when(classifiers.findByElementModelIdOrderByElementNameAsc(model.getId()))
                .thenReturn(List.of(client, user));
        UmlProperty email = property("email", user, PrimitiveType.STRING, 1, 1);
        when(properties.findByOwnerClassifierElementIdOrderByElementNameAscElementIdAsc(
                user.getElementId())).thenReturn(List.of(email));
        when(properties.findByOwnerClassifierElementIdOrderByElementNameAscElementIdAsc(
                client.getElementId())).thenReturn(List.of());
        UmlOperation register = operation("registrar", user);
        when(operations.findByOwnerClassifierElementIdOrderByElementNameAscElementIdAsc(
                user.getElementId())).thenReturn(List.of(register));
        when(operations.findByOwnerClassifierElementIdOrderByElementNameAscElementIdAsc(
                client.getElementId())).thenReturn(List.of());
        UUID operationId = register.getElementId();
        UmlParameter nameParameter = parameter("nombre", register, PrimitiveType.STRING);
        when(parameters.findByOperationElementIdOrderByPositionAsc(operationId))
                .thenReturn(List.of(nameParameter));
        UmlGeneralization inheritance = mock(UmlGeneralization.class);
        when(inheritance.getElementId()).thenReturn(UUID.randomUUID());
        when(inheritance.getSpecificClassifier()).thenReturn(client);
        when(inheritance.getGeneralClassifier()).thenReturn(user);
        when(generalizations.findByElementModelIdOrderByElementCreatedAtAsc(model.getId()))
                .thenReturn(List.of(inheritance));
        UmlAssociation association = mock(UmlAssociation.class);
        UUID associationId = UUID.randomUUID();
        when(association.getElementId()).thenReturn(associationId);
        UmlAssociationEnd clientEnd = associationEnd(client, 0, null);
        UmlAssociationEnd userEnd = associationEnd(user, 1, 1);
        when(associations.findByElementModelIdOrderByElementCreatedAtAsc(model.getId()))
                .thenReturn(List.of(association));
        when(associationEnds.findByAssociationElementIdOrderByMemberOrderAsc(associationId))
                .thenReturn(List.of(clientEnd, userEnd));

        String summary = builder.build(model, null);

        assertTrue(summary.contains("Clase Usuario"));
        assertTrue(summary.contains("atributo email: STRING [1..1]"));
        assertTrue(summary.contains("operación registrar(nombre: STRING)"));
        assertTrue(summary.contains("Tipo: GENERALIZATION | Subclase: Cliente"
                + " | Superclase: Usuario | Significado: Cliente hereda de Usuario"));
        assertTrue(summary.contains("Tipo: ASSOCIATION | Extremo A: Cliente multiplicidad [0..*]"
                + " agregación NONE | Extremo B: Usuario multiplicidad [1..1] agregación NONE"));
        assertFalse(summary.contains("x:"));
        assertFalse(summary.contains("width"));
    }

    @Test
    void diagramScopeIncludesOnlyClassifiersRepresentedByNodes() {
        UmlModel model = model();
        Diagram diagram = mock(Diagram.class);
        when(diagram.getId()).thenReturn(UUID.randomUUID());
        UmlClassifier visible = classifier("Visible", ClassifierKind.CLASS);
        UmlClassifier hidden = classifier("Oculta", ClassifierKind.CLASS);
        when(classifiers.findByElementModelIdOrderByElementNameAsc(model.getId()))
                .thenReturn(List.of(hidden, visible));
        DiagramNode node = mock(DiagramNode.class);
        UmlElement visibleElement = visible.getElement();
        when(node.getUmlElement()).thenReturn(visibleElement);
        when(nodes.findByDiagramIdOrderByZIndexAscCreatedAtAsc(diagram.getId()))
                .thenReturn(List.of(node));
        when(edges.findByDiagramIdOrderByCreatedAtAsc(diagram.getId())).thenReturn(List.of());
        when(properties.findByOwnerClassifierElementIdOrderByElementNameAscElementIdAsc(any()))
                .thenReturn(List.of());
        when(operations.findByOwnerClassifierElementIdOrderByElementNameAscElementIdAsc(any()))
                .thenReturn(List.of());

        String summary = builder.build(model, diagram);

        assertTrue(summary.contains("Clase Visible"));
        assertFalse(summary.contains("Oculta"));
    }

    @Test
    void importedDiagramGeneralizationsAreExplicitAndScopedByEdges() {
        UmlModel model = model();
        Diagram diagram = mock(Diagram.class);
        when(diagram.getId()).thenReturn(UUID.randomUUID());
        UmlClassifier user = classifier("Usuario", ClassifierKind.CLASS);
        UmlClassifier client = classifier("Cliente", ClassifierKind.CLASS);
        UmlClassifier employee = classifier("Empleado", ClassifierKind.CLASS);
        when(classifiers.findByElementModelIdOrderByElementNameAsc(model.getId()))
                .thenReturn(List.of(client, employee, user));
        when(properties.findByOwnerClassifierElementIdOrderByElementNameAscElementIdAsc(any()))
                .thenReturn(List.of());
        when(operations.findByOwnerClassifierElementIdOrderByElementNameAscElementIdAsc(any()))
                .thenReturn(List.of());
        DiagramNode userNode = node(user);
        DiagramNode clientNode = node(client);
        DiagramNode employeeNode = node(employee);
        when(nodes.findByDiagramIdOrderByZIndexAscCreatedAtAsc(diagram.getId()))
                .thenReturn(List.of(userNode, clientNode, employeeNode));
        UmlGeneralization clientInheritance = generalization(client, user);
        UmlGeneralization employeeInheritance = generalization(employee, user);
        when(generalizations.findByElementModelIdOrderByElementCreatedAtAsc(model.getId()))
                .thenReturn(List.of(clientInheritance, employeeInheritance));
        DiagramEdge clientEdge = edge(clientInheritance.getElementId());
        DiagramEdge employeeEdge = edge(employeeInheritance.getElementId());
        when(edges.findByDiagramIdOrderByCreatedAtAsc(diagram.getId()))
                .thenReturn(List.of(clientEdge, employeeEdge));

        String summary = builder.build(model, diagram);

        assertTrue(summary.contains("Subclase: Cliente | Superclase: Usuario"
                + " | Significado: Cliente hereda de Usuario"));
        assertTrue(summary.contains("Subclase: Empleado | Superclase: Usuario"
                + " | Significado: Empleado hereda de Usuario"));
        assertFalse(summary.contains("--|>"));
    }

    private UmlModel model() {
        UmlModel value = mock(UmlModel.class);
        when(value.getId()).thenReturn(UUID.randomUUID());
        return value;
    }

    private UmlClassifier classifier(String name, ClassifierKind kind) {
        UmlElement element = element(name);
        UUID elementId = element.getId();
        UmlClassifier value = mock(UmlClassifier.class);
        when(value.getElementId()).thenReturn(elementId);
        when(value.getElement()).thenReturn(element);
        when(value.getKind()).thenReturn(kind);
        return value;
    }

    private DiagramNode node(UmlClassifier classifier) {
        DiagramNode value = mock(DiagramNode.class);
        UmlElement element = classifier.getElement();
        when(value.getUmlElement()).thenReturn(element);
        return value;
    }

    private DiagramEdge edge(UUID relationId) {
        DiagramEdge value = mock(DiagramEdge.class);
        UmlElement element = mock(UmlElement.class);
        when(element.getId()).thenReturn(relationId);
        when(value.getUmlElement()).thenReturn(element);
        return value;
    }

    private UmlGeneralization generalization(UmlClassifier subclass, UmlClassifier superclass) {
        UmlGeneralization value = mock(UmlGeneralization.class);
        when(value.getElementId()).thenReturn(UUID.randomUUID());
        when(value.getSpecificClassifier()).thenReturn(subclass);
        when(value.getGeneralClassifier()).thenReturn(superclass);
        return value;
    }

    private UmlProperty property(String name, UmlClassifier owner, PrimitiveType type,
            int lower, Integer upper) {
        UmlProperty value = mock(UmlProperty.class);
        UmlElement element = element(name);
        when(value.getElement()).thenReturn(element);
        when(value.getOwnerClassifier()).thenReturn(owner);
        when(value.getTypeKind()).thenReturn(TypeKind.PRIMITIVE);
        when(value.getPrimitiveType()).thenReturn(type);
        when(value.getLowerBound()).thenReturn(lower);
        when(value.getUpperBound()).thenReturn(upper);
        return value;
    }

    private UmlOperation operation(String name, UmlClassifier owner) {
        UmlOperation value = mock(UmlOperation.class);
        UmlElement element = element(name);
        UUID elementId = element.getId();
        when(value.getElementId()).thenReturn(elementId);
        when(value.getElement()).thenReturn(element);
        when(value.getOwnerClassifier()).thenReturn(owner);
        return value;
    }

    private UmlParameter parameter(String name, UmlOperation operation, PrimitiveType type) {
        UmlParameter value = mock(UmlParameter.class);
        UmlElement element = element(name);
        when(value.getElement()).thenReturn(element);
        when(value.getOperation()).thenReturn(operation);
        when(value.getDirection()).thenReturn(ParameterDirection.IN);
        when(value.getTypeKind()).thenReturn(TypeKind.PRIMITIVE);
        when(value.getPrimitiveType()).thenReturn(type);
        return value;
    }

    private UmlAssociationEnd associationEnd(UmlClassifier type, int lower, Integer upper) {
        UmlProperty property = mock(UmlProperty.class);
        when(property.getTypeClassifier()).thenReturn(type);
        when(property.getLowerBound()).thenReturn(lower);
        when(property.getUpperBound()).thenReturn(upper);
        UmlAssociationEnd end = mock(UmlAssociationEnd.class);
        when(end.getProperty()).thenReturn(property);
        when(end.getAggregation()).thenReturn(AggregationKind.NONE);
        return end;
    }

    private UmlElement element(String name) {
        UmlElement value = mock(UmlElement.class);
        when(value.getId()).thenReturn(UUID.randomUUID());
        when(value.getName()).thenReturn(name);
        return value;
    }
}
