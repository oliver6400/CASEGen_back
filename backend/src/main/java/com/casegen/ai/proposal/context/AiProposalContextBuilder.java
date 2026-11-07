package com.casegen.ai.proposal.context;

import com.casegen.ai.proposal.validation.AiProposalValidationErrorCode;
import com.casegen.ai.proposal.validation.AiProposalValidationException;
import com.casegen.modeling.domain.Diagram;
import com.casegen.modeling.domain.UmlAssociation;
import com.casegen.modeling.domain.UmlAssociationEnd;
import com.casegen.modeling.domain.UmlClassifier;
import com.casegen.modeling.domain.UmlElementType;
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
import com.casegen.project.repository.ProjectRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AiProposalContextBuilder {
    private final ProjectRepository projects;
    private final UmlModelRepository models;
    private final DiagramRepository diagrams;
    private final DiagramNodeRepository nodes;
    private final DiagramEdgeRepository edges;
    private final UmlPackageRepository packages;
    private final UmlClassifierRepository classifiers;
    private final UmlPropertyRepository properties;
    private final UmlOperationRepository operations;
    private final UmlAssociationRepository associations;
    private final UmlAssociationEndRepository associationEnds;
    private final UmlGeneralizationRepository generalizations;
    private final UmlRealizationRepository realizations;

    public AiProposalContextBuilder(ProjectRepository projects, UmlModelRepository models,
            DiagramRepository diagrams, DiagramNodeRepository nodes, DiagramEdgeRepository edges,
            UmlPackageRepository packages, UmlClassifierRepository classifiers,
            UmlPropertyRepository properties, UmlOperationRepository operations,
            UmlAssociationRepository associations, UmlAssociationEndRepository associationEnds,
            UmlGeneralizationRepository generalizations, UmlRealizationRepository realizations) {
        this.projects = projects;
        this.models = models;
        this.diagrams = diagrams;
        this.nodes = nodes;
        this.edges = edges;
        this.packages = packages;
        this.classifiers = classifiers;
        this.properties = properties;
        this.operations = operations;
        this.associations = associations;
        this.associationEnds = associationEnds;
        this.generalizations = generalizations;
        this.realizations = realizations;
    }

    @Transactional(readOnly = true)
    public AiProposalContext build(UUID projectId, UUID modelId, UUID diagramId) {
        if (projectId == null || !projects.existsById(projectId)) {
            throw missing("Project does not exist");
        }
        var model = models.findById(modelId).orElseThrow(() -> missing("UML model does not exist"));
        if (!model.getProject().getId().equals(projectId)) {
            throw outside("UML model belongs to a different project");
        }
        Diagram diagram = null;
        if (diagramId != null) {
            diagram = diagrams.findById(diagramId)
                    .orElseThrow(() -> missing("Diagram does not exist"));
            if (!diagram.getModel().getId().equals(modelId)) {
                throw outside("Diagram belongs to a different UML model");
            }
        }

        Set<UUID> visibleElements = new HashSet<>();
        Set<UUID> visibleRelationships = new HashSet<>();
        if (diagram != null) {
            nodes.findByDiagramIdOrderByZIndexAscCreatedAtAsc(diagramId)
                    .forEach(node -> visibleElements.add(node.getUmlElement().getId()));
            edges.findByDiagramIdOrderByCreatedAtAsc(diagramId)
                    .forEach(edge -> visibleRelationships.add(edge.getUmlElement().getId()));
        }
        boolean wholeModelVisible = diagram == null;

        List<AiProposalContext.PackageEntry> packageEntries = packages
                .findByElementModelIdOrderByElementNameAsc(modelId).stream()
                .map(value -> new AiProposalContext.PackageEntry(value.getElementId(),
                        value.getElement().getName(), value.getParentPackage() == null ? null
                                : value.getParentPackage().getElementId(),
                        wholeModelVisible || visibleElements.contains(value.getElementId())))
                .toList();
        List<UmlClassifier> modelClassifiers = classifiers
                .findByElementModelIdOrderByElementNameAsc(modelId);
        List<AiProposalContext.ClassifierEntry> classifierEntries = modelClassifiers.stream()
                .map(value -> new AiProposalContext.ClassifierEntry(value.getElementId(),
                        value.getElement().getName(), value.getKind(), value.getUmlPackage() == null
                                ? null : value.getUmlPackage().getElementId(),
                        wholeModelVisible || visibleElements.contains(value.getElementId())))
                .toList();
        List<AiProposalContext.PropertyEntry> propertyEntries = new ArrayList<>();
        List<AiProposalContext.OperationEntry> operationEntries = new ArrayList<>();
        for (UmlClassifier classifier : modelClassifiers) {
            boolean visible = wholeModelVisible || visibleElements.contains(classifier.getElementId());
            properties.findByOwnerClassifierElementIdOrderByElementNameAscElementIdAsc(
                    classifier.getElementId()).forEach(value -> propertyEntries.add(
                            new AiProposalContext.PropertyEntry(value.getElementId(),
                                    classifier.getElementId(), value.getElement().getName(), visible)));
            operations.findByOwnerClassifierElementIdOrderByElementNameAscElementIdAsc(
                    classifier.getElementId()).forEach(value -> operationEntries.add(
                            new AiProposalContext.OperationEntry(value.getElementId(),
                                    classifier.getElementId(), value.getElement().getName(), visible)));
        }

        List<AiProposalContext.RelationshipEntry> relationshipEntries = new ArrayList<>();
        for (UmlAssociation association
                : associations.findByElementModelIdOrderByElementCreatedAtAsc(modelId)) {
            List<UmlAssociationEnd> ends = associationEnds
                    .findByAssociationElementIdOrderByMemberOrderAsc(association.getElementId());
            if (ends.size() == 2) {
                relationshipEntries.add(relationship(association.getElementId(),
                        UmlElementType.ASSOCIATION,
                        ends.get(0).getProperty().getTypeClassifier().getElementId(),
                        ends.get(1).getProperty().getTypeClassifier().getElementId(),
                        wholeModelVisible, visibleRelationships));
            }
        }
        generalizations.findByElementModelIdOrderByElementCreatedAtAsc(modelId).forEach(value ->
                relationshipEntries.add(relationship(value.getElementId(), UmlElementType.GENERALIZATION,
                        value.getSpecificClassifier().getElementId(),
                        value.getGeneralClassifier().getElementId(), wholeModelVisible, visibleRelationships)));
        realizations.findByElementModelIdOrderByElementCreatedAtAsc(modelId).forEach(value ->
                relationshipEntries.add(relationship(value.getElementId(), UmlElementType.REALIZATION,
                        value.getClientClassifier().getElementId(),
                        value.getSupplierClassifier().getElementId(), wholeModelVisible, visibleRelationships)));

        return new AiProposalContext(projectId, modelId, model.getRevision(), diagramId,
                diagram == null ? null : diagram.getRevision(), packageEntries, classifierEntries,
                propertyEntries, operationEntries, relationshipEntries);
    }

    private AiProposalContext.RelationshipEntry relationship(UUID id, UmlElementType type,
            UUID first, UUID second, boolean wholeModelVisible, Set<UUID> visible) {
        return new AiProposalContext.RelationshipEntry(id, type, first, second,
                wholeModelVisible || visible.contains(id));
    }

    private AiProposalValidationException missing(String message) {
        return new AiProposalValidationException(
                AiProposalValidationErrorCode.REFERENCED_ELEMENT_NOT_FOUND, message);
    }

    private AiProposalValidationException outside(String message) {
        return new AiProposalValidationException(
                AiProposalValidationErrorCode.ELEMENT_OUTSIDE_MODEL, message);
    }
}
