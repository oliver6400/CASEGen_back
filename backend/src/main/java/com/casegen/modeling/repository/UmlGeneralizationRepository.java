package com.casegen.modeling.repository;

import com.casegen.modeling.domain.UmlGeneralization;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UmlGeneralizationRepository extends JpaRepository<UmlGeneralization, UUID> {

    @EntityGraph(attributePaths = {"element", "specificClassifier", "generalClassifier"})
    List<UmlGeneralization> findByElementModelIdOrderByElementCreatedAtAsc(UUID modelId);

    boolean existsBySpecificClassifierElementIdAndGeneralClassifierElementId(UUID specificId, UUID generalId);
}
