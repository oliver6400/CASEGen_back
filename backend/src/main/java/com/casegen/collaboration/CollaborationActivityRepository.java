package com.casegen.collaboration;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CollaborationActivityRepository extends JpaRepository<CollaborationActivity, UUID> {
    @Query("""
            select activity from CollaborationActivity activity
            where activity.model.id = :modelId
              and (activity.diagram is null or activity.diagram.id = :diagramId)
            order by activity.createdAt desc
            """)
    List<CollaborationActivity> findRecentForDiagram(
            @Param("modelId") UUID modelId, @Param("diagramId") UUID diagramId);
}
