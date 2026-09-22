package com.casegen.modeling.repository;

import com.casegen.modeling.domain.UmlOperation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UmlOperationRepository extends JpaRepository<UmlOperation, UUID> {

    @EntityGraph(attributePaths = {"element", "ownerClassifier"})
    List<UmlOperation> findByOwnerClassifierElementIdOrderByElementNameAscElementIdAsc(UUID classifierId);
}
