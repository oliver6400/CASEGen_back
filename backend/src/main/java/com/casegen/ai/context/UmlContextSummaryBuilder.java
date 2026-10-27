package com.casegen.ai.context;

import com.casegen.modeling.domain.Diagram;
import com.casegen.modeling.domain.TypeKind;
import com.casegen.modeling.domain.UmlAssociation;
import com.casegen.modeling.domain.UmlAssociationEnd;
import com.casegen.modeling.domain.UmlClassifier;
import com.casegen.modeling.domain.UmlGeneralization;
import com.casegen.modeling.domain.UmlModel;
import com.casegen.modeling.domain.UmlOperation;
import com.casegen.modeling.domain.UmlParameter;
import com.casegen.modeling.domain.UmlProperty;
import com.casegen.modeling.domain.UmlRealization;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class UmlContextSummaryBuilder {

    private static final int MAX_CLASSIFIERS = 50;
    private final UmlClassifierRepository classifiers;
    private final UmlPropertyRepository properties;
    private final UmlOperationRepository operations;
    private final UmlParameterRepository parameters;
    private final UmlGeneralizationRepository generalizations;
    private final UmlRealizationRepository realizations;
    private final UmlAssociationRepository associations;
    private final UmlAssociationEndRepository associationEnds;
    private final DiagramNodeRepository diagramNodes;
    private final DiagramEdgeRepository diagramEdges;

    public UmlContextSummaryBuilder(UmlClassifierRepository classifiers,
            UmlPropertyRepository properties, UmlOperationRepository operations,
            UmlParameterRepository parameters, UmlGeneralizationRepository generalizations,
            UmlRealizationRepository realizations, UmlAssociationRepository associations,
            UmlAssociationEndRepository associationEnds, DiagramNodeRepository diagramNodes,
            DiagramEdgeRepository diagramEdges) {
        this.classifiers = classifiers;
        this.properties = properties;
        this.operations = operations;
        this.parameters = parameters;
        this.generalizations = generalizations;
        this.realizations = realizations;
        this.associations = associations;
        this.associationEnds = associationEnds;
        this.diagramNodes = diagramNodes;
        this.diagramEdges = diagramEdges;
    }

    public String build(UmlModel model, Diagram diagram) {
        Set<UUID> classifierScope = null;
        Set<UUID> relationScope = null;
        if (diagram != null) {
            classifierScope = new HashSet<>();
            for (var node : diagramNodes.findByDiagramIdOrderByZIndexAscCreatedAtAsc(diagram.getId())) {
                classifierScope.add(node.getUmlElement().getId());
            }
            relationScope = new HashSet<>();
            for (var edge : diagramEdges.findByDiagramIdOrderByCreatedAtAsc(diagram.getId())) {
                relationScope.add(edge.getUmlElement().getId());
            }
        }
        final Set<UUID> visibleClassifiers = classifierScope;
        final Set<UUID> visibleRelations = relationScope;

        List<UmlClassifier> selected = classifiers
                .findByElementModelIdOrderByElementNameAsc(model.getId()).stream()
                .filter(value -> visibleClassifiers == null
                        || visibleClassifiers.contains(value.getElementId()))
                .limit(MAX_CLASSIFIERS)
                .toList();
        Set<UUID> selectedIds = selected.stream().map(UmlClassifier::getElementId)
                .collect(java.util.stream.Collectors.toSet());
        StringBuilder summary = new StringBuilder("ELEMENTOS UML\n");
        for (UmlClassifier classifier : selected) appendClassifier(summary, classifier);
        appendRelations(summary, model.getId(), selectedIds, visibleRelations);
        if (selected.isEmpty()) summary.append("(sin clasificadores relevantes)\n");
        return summary.toString().trim();
    }

    private void appendClassifier(StringBuilder out, UmlClassifier classifier) {
        out.append(classifier.getKind() == com.casegen.modeling.domain.ClassifierKind.INTERFACE
                ? "Interfaz " : "Clase ").append(name(classifier)).append('\n');
        for (UmlProperty property : properties
                .findByOwnerClassifierElementIdOrderByElementNameAscElementIdAsc(
                        classifier.getElementId())) {
            out.append("- atributo ").append(safe(property.getElement().getName()))
                    .append(": ").append(type(property.getTypeKind(), property.getPrimitiveType(),
                            property.getTypeClassifier()))
                    .append(' ').append(multiplicity(property.getLowerBound(), property.getUpperBound()))
                    .append('\n');
        }
        for (UmlOperation operation : operations
                .findByOwnerClassifierElementIdOrderByElementNameAscElementIdAsc(
                        classifier.getElementId())) {
            out.append("- operación ").append(safe(operation.getElement().getName())).append('(');
            List<UmlParameter> values = parameters
                    .findByOperationElementIdOrderByPositionAsc(operation.getElementId());
            for (int index = 0; index < values.size(); index++) {
                if (index > 0) out.append(", ");
                UmlParameter parameter = values.get(index);
                out.append(safe(parameter.getElement().getName())).append(": ")
                        .append(type(parameter.getTypeKind(), parameter.getPrimitiveType(),
                                parameter.getTypeClassifier()));
            }
            out.append(")\n");
        }
    }

    private void appendRelations(StringBuilder out, UUID modelId, Set<UUID> selected,
            Set<UUID> visibleRelations) {
        out.append("RELACIONES\n");
        for (UmlGeneralization relation : generalizations
                .findByElementModelIdOrderByElementCreatedAtAsc(modelId)) {
            if (include(relation.getElementId(), relation.getSpecificClassifier().getElementId(),
                    relation.getGeneralClassifier().getElementId(), selected, visibleRelations)) {
                String subclass = name(relation.getSpecificClassifier());
                String superclass = name(relation.getGeneralClassifier());
                out.append("Tipo: GENERALIZATION | Subclase: ").append(subclass)
                        .append(" | Superclase: ").append(superclass)
                        .append(" | Significado: ").append(subclass).append(" hereda de ")
                        .append(superclass).append('\n');
            }
        }
        for (UmlRealization relation : realizations
                .findByElementModelIdOrderByElementCreatedAtAsc(modelId)) {
            if (include(relation.getElementId(), relation.getClientClassifier().getElementId(),
                    relation.getSupplierClassifier().getElementId(), selected, visibleRelations)) {
                String implementer = name(relation.getClientClassifier());
                String contract = name(relation.getSupplierClassifier());
                out.append("Tipo: REALIZATION | Implementador: ").append(implementer)
                        .append(" | Contrato: ").append(contract)
                        .append(" | Significado: ").append(implementer).append(" implementa ")
                        .append(contract).append('\n');
            }
        }
        for (UmlAssociation association : associations
                .findByElementModelIdOrderByElementCreatedAtAsc(modelId)) {
            List<UmlAssociationEnd> ends = associationEnds
                    .findByAssociationElementIdOrderByMemberOrderAsc(association.getElementId());
            if (ends.size() == 2 && include(association.getElementId(),
                    ends.get(0).getProperty().getTypeClassifier().getElementId(),
                    ends.get(1).getProperty().getTypeClassifier().getElementId(),
                    selected, visibleRelations)) {
                UmlAssociationEnd first = ends.get(0);
                UmlAssociationEnd second = ends.get(1);
                out.append("Tipo: ASSOCIATION | Extremo A: ")
                        .append(name(first.getProperty().getTypeClassifier()))
                        .append(" multiplicidad ").append(multiplicity(
                                first.getProperty().getLowerBound(), first.getProperty().getUpperBound()))
                        .append(" agregación ").append(first.getAggregation())
                        .append(" | Extremo B: ")
                        .append(name(second.getProperty().getTypeClassifier()))
                        .append(" multiplicidad ").append(multiplicity(
                                second.getProperty().getLowerBound(), second.getProperty().getUpperBound()))
                        .append(" agregación ").append(second.getAggregation()).append('\n');
            }
        }
    }

    private boolean include(UUID relationId, UUID source, UUID target, Set<UUID> selected,
            Set<UUID> visibleRelations) {
        return selected.contains(source) && selected.contains(target)
                && (visibleRelations == null || visibleRelations.contains(relationId));
    }

    private String type(TypeKind kind, Object primitive, UmlClassifier classifier) {
        return kind == TypeKind.PRIMITIVE ? String.valueOf(primitive) : name(classifier);
    }

    private String multiplicity(int lower, Integer upper) {
        return "[" + lower + ".." + (upper == null ? "*" : upper) + "]";
    }

    private String name(UmlClassifier classifier) {
        return classifier == null ? "desconocido" : safe(classifier.getElement().getName());
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "sin nombre" : value.replaceAll("[\\r\\n]+", " ").trim();
    }
}
