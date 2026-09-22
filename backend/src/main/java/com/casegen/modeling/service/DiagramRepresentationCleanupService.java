package com.casegen.modeling.service;

import com.casegen.modeling.domain.DiagramEdge;
import com.casegen.modeling.repository.DiagramEdgeRepository;
import com.casegen.modeling.repository.DiagramNodeRepository;
import com.casegen.modeling.repository.DiagramRepository;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DiagramRepresentationCleanupService {

    private final DiagramNodeRepository nodeRepository;
    private final DiagramEdgeRepository edgeRepository;
    private final DiagramRepository diagramRepository;

    public DiagramRepresentationCleanupService(
            DiagramNodeRepository nodeRepository,
            DiagramEdgeRepository edgeRepository,
            DiagramRepository diagramRepository) {
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
        this.diagramRepository = diagramRepository;
    }

    public void removeClassifierRepresentations(UUID classifierId) {
        var connectedEdges = new LinkedHashSet<DiagramEdge>(
                edgeRepository.findConnectedToUmlElement(classifierId));
        var nodes = nodeRepository.findByUmlElementId(classifierId);
        Set<UUID> affectedDiagramIds = new TreeSet<>();
        connectedEdges.forEach(edge -> affectedDiagramIds.add(edge.getDiagram().getId()));
        nodes.forEach(node -> affectedDiagramIds.add(node.getDiagram().getId()));
        if (!connectedEdges.isEmpty()) {
            edgeRepository.deleteAll(connectedEdges);
            edgeRepository.flush();
        }
        if (!nodes.isEmpty()) {
            nodeRepository.deleteAll(nodes);
            nodeRepository.flush();
        }
        recordCleanup(affectedDiagramIds);
    }

    public void removeRelationshipRepresentations(UUID relationshipId) {
        var edges = edgeRepository.findByUmlElementId(relationshipId);
        Set<UUID> affectedDiagramIds = new TreeSet<>();
        edges.forEach(edge -> affectedDiagramIds.add(edge.getDiagram().getId()));
        if (!edges.isEmpty()) {
            edgeRepository.deleteAll(edges);
            edgeRepository.flush();
        }
        recordCleanup(affectedDiagramIds);
    }

    private void recordCleanup(Set<UUID> diagramIds) {
        for (UUID diagramId : diagramIds) {
            diagramRepository.findByIdForUpdate(diagramId).ifPresent(diagram -> {
                diagram.recordNotationMutation();
                diagramRepository.saveAndFlush(diagram);
            });
        }
    }
}
