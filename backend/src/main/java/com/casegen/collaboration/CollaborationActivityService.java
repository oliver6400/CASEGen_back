package com.casegen.collaboration;

import com.casegen.auth.domain.User;
import com.casegen.auth.repository.UserRepository;
import com.casegen.modeling.domain.Diagram;
import com.casegen.modeling.domain.DiagramEdge;
import com.casegen.modeling.domain.DiagramNode;
import com.casegen.modeling.domain.UmlElement;
import com.casegen.modeling.domain.UmlModel;
import com.casegen.modeling.repository.DiagramEdgeRepository;
import com.casegen.modeling.repository.DiagramNodeRepository;
import com.casegen.modeling.repository.DiagramRepository;
import com.casegen.modeling.repository.UmlElementRepository;
import com.casegen.modeling.repository.UmlAssociationEndRepository;
import com.casegen.modeling.repository.UmlGeneralizationRepository;
import com.casegen.modeling.repository.UmlRealizationRepository;
import com.casegen.modeling.repository.UmlModelRepository;
import com.casegen.project.domain.Project;
import com.casegen.project.service.ProjectAuthorizationService;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CollaborationActivityService {
    private static final EnumSet<RealtimeEventType> RECORDED = EnumSet.of(
            RealtimeEventType.PACKAGE_CREATED, RealtimeEventType.PACKAGE_UPDATED,
            RealtimeEventType.PACKAGE_DELETED, RealtimeEventType.CLASSIFIER_CREATED, RealtimeEventType.CLASSIFIER_UPDATED,
            RealtimeEventType.CLASSIFIER_DELETED, RealtimeEventType.PROPERTY_CREATED,
            RealtimeEventType.PROPERTY_UPDATED, RealtimeEventType.PROPERTY_DELETED,
            RealtimeEventType.OPERATION_CREATED, RealtimeEventType.OPERATION_UPDATED,
            RealtimeEventType.OPERATION_DELETED, RealtimeEventType.PARAMETER_CREATED,
            RealtimeEventType.PARAMETER_UPDATED, RealtimeEventType.PARAMETER_DELETED,
            RealtimeEventType.RELATIONSHIP_CREATED,
            RealtimeEventType.RELATIONSHIP_UPDATED, RealtimeEventType.RELATIONSHIP_DELETED,
            RealtimeEventType.DIAGRAM_NODE_ADDED, RealtimeEventType.DIAGRAM_NODE_MOVED,
            RealtimeEventType.DIAGRAM_NODE_RESIZED, RealtimeEventType.DIAGRAM_NODE_REMOVED,
            RealtimeEventType.DIAGRAM_CREATED, RealtimeEventType.DIAGRAM_DELETED);

    private final CollaborationActivityRepository activities;
    private final UserRepository users;
    private final UmlModelRepository models;
    private final DiagramRepository diagrams;
    private final UmlElementRepository elements;
    private final DiagramNodeRepository nodes;
    private final DiagramEdgeRepository edges;
    private final UmlAssociationEndRepository associationEnds;
    private final UmlGeneralizationRepository generalizations;
    private final UmlRealizationRepository realizations;
    private final ProjectAuthorizationService authorization;

    public CollaborationActivityService(CollaborationActivityRepository activities,
            UserRepository users, UmlModelRepository models, DiagramRepository diagrams,
            UmlElementRepository elements, DiagramNodeRepository nodes, DiagramEdgeRepository edges,
            UmlAssociationEndRepository associationEnds, UmlGeneralizationRepository generalizations,
            UmlRealizationRepository realizations,
            ProjectAuthorizationService authorization) {
        this.activities = activities;
        this.users = users;
        this.models = models;
        this.diagrams = diagrams;
        this.elements = elements;
        this.nodes = nodes;
        this.edges = edges;
        this.associationEnds = associationEnds;
        this.generalizations = generalizations;
        this.realizations = realizations;
        this.authorization = authorization;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CollaborationActivityResponse recordConfirmed(RealtimeEvent event) {
        if (!RECORDED.contains(event.eventType())) return null;
        User actor = users.findById(event.actorUserId()).orElse(null);
        UmlModel model = models.findById(event.modelId()).orElse(null);
        if (actor == null || model == null) return null;
        Diagram diagram = event.diagramId() == null ? null : diagrams.findById(event.diagramId()).orElse(null);
        String subject = resolveSubject(event);
        CollaborationActivity saved = activities.save(new CollaborationActivity(model.getProject(), model, diagram, actor,
                event.eventType(), event.entityId(), describe(actor.getDisplayName(), event.eventType(), subject),
                event.modelRevision(), event.diagramRevision(), event.occurredAt()));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CollaborationActivityResponse> listForDiagram(UUID diagramId) {
        Diagram diagram = diagrams.findById(diagramId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Diagram does not exist"));
        authorization.requireRead(diagram.getModel().getProject().getId());
        return activities.findRecentForDiagram(diagram.getModel().getId(), diagramId).stream()
                .limit(100)
                .map(this::toResponse)
                .toList();
    }

    private String resolveSubject(RealtimeEvent event) {
        if (event.entityId() == null) return fallbackSubject(event.eventType());
        if (event.eventType().name().startsWith("RELATIONSHIP_")) {
            String relationship = relationshipSubject(event.entityId());
            if (relationship != null) return relationship;
        }
        UmlElement element = elements.findById(event.entityId()).orElse(null);
        if (element != null && element.getName() != null && !element.getName().isBlank()) return element.getName();
        DiagramNode node = nodes.findById(event.entityId()).orElse(null);
        if (node != null && node.getUmlElement().getName() != null) return node.getUmlElement().getName();
        DiagramEdge edge = edges.findById(event.entityId()).orElse(null);
        if (edge != null) return "una relación";
        return fallbackSubject(event.eventType());
    }

    private String relationshipSubject(UUID relationshipId) {
        var generalization = generalizations.findById(relationshipId).orElse(null);
        if (generalization != null) return "relación "
                + generalization.getSpecificClassifier().getElement().getName() + " → "
                + generalization.getGeneralClassifier().getElement().getName();
        var realization = realizations.findById(relationshipId).orElse(null);
        if (realization != null) return "relación "
                + realization.getClientClassifier().getElement().getName() + " → "
                + realization.getSupplierClassifier().getElement().getName();
        var ends = associationEnds.findByAssociationElementIdOrderByMemberOrderAsc(relationshipId);
        if (ends.size() == 2) return "relación "
                + ends.get(0).getProperty().getTypeClassifier().getElement().getName() + " → "
                + ends.get(1).getProperty().getTypeClassifier().getElement().getName();
        return null;
    }

    private String fallbackSubject(RealtimeEventType type) {
        if (type.name().startsWith("RELATIONSHIP_") || type.name().startsWith("DIAGRAM_EDGE_")) {
            return "una relación";
        }
        if (type.name().startsWith("DIAGRAM_")) return "el diagrama";
        return "un elemento";
    }

    private String describe(String actor, RealtimeEventType type, String subject) {
        String action = switch (type) {
            case CLASSIFIER_CREATED -> "creó";
            case PROPERTY_CREATED, OPERATION_CREATED -> "agregó";
            case RELATIONSHIP_CREATED -> "creó";
            case DIAGRAM_NODE_MOVED -> "movió";
            case DIAGRAM_NODE_RESIZED -> "redimensionó";
            case DIAGRAM_NODE_ADDED -> "añadió";
            case CLASSIFIER_DELETED, PROPERTY_DELETED, OPERATION_DELETED,
                    RELATIONSHIP_DELETED, DIAGRAM_NODE_REMOVED, DIAGRAM_DELETED -> "eliminó";
            case DIAGRAM_CREATED -> "creó";
            default -> "actualizó";
        };
        return actor + " " + action + " " + subject;
    }

    private CollaborationActivityResponse toResponse(CollaborationActivity activity) {
        return new CollaborationActivityResponse(activity.getId(), activity.getActor().getId(),
                activity.getActor().getDisplayName(), activity.getEventType(), activity.getElementId(),
                activity.getDescription(), activity.getModelRevision(), activity.getDiagramRevision(),
                activity.getCreatedAt());
    }
}
