package com.casegen.modeling.repository;

import com.casegen.modeling.domain.UmlClassifier;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UmlClassifierRepository extends JpaRepository<UmlClassifier, UUID> {

    @EntityGraph(attributePaths = {"element", "umlPackage"})
    List<UmlClassifier> findByElementModelIdOrderByElementNameAsc(UUID modelId);
}
