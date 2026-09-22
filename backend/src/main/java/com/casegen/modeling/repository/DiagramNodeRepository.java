package com.casegen.modeling.repository;

import com.casegen.modeling.domain.DiagramNode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiagramNodeRepository extends JpaRepository<DiagramNode, UUID> {

    @Query("""
            select node from DiagramNode node
            where node.diagram.id = :diagramId
            order by node.zIndex asc, node.createdAt asc
            """)
    List<DiagramNode> findByDiagramIdOrderByZIndexAscCreatedAtAsc(
            @Param("diagramId") UUID diagramId);

    Optional<DiagramNode> findByIdAndDiagramId(UUID id, UUID diagramId);

    boolean existsByDiagramIdAndUmlElementId(UUID diagramId, UUID umlElementId);

    List<DiagramNode> findByUmlElementId(UUID umlElementId);
}
