package com.casegen.modeling.repository;

import com.casegen.modeling.domain.DiagramEdge;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiagramEdgeRepository extends JpaRepository<DiagramEdge, UUID> {

    List<DiagramEdge> findByDiagramIdOrderByCreatedAtAsc(UUID diagramId);

    Optional<DiagramEdge> findByIdAndDiagramId(UUID id, UUID diagramId);

    boolean existsByDiagramIdAndUmlElementId(UUID diagramId, UUID umlElementId);

    List<DiagramEdge> findByUmlElementId(UUID umlElementId);

    @Query("""
            select distinct edge from DiagramEdge edge
            where edge.sourceNode.umlElement.id = :umlElementId
               or edge.targetNode.umlElement.id = :umlElementId
            """)
    List<DiagramEdge> findConnectedToUmlElement(@Param("umlElementId") UUID umlElementId);
}
