package com.casegen.modeling.repository;

import com.casegen.modeling.domain.UmlRealization;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UmlRealizationRepository extends JpaRepository<UmlRealization, UUID> {

    @EntityGraph(attributePaths = {"element", "clientClassifier", "supplierClassifier"})
    List<UmlRealization> findByElementModelIdOrderByElementCreatedAtAsc(UUID modelId);

    boolean existsByClientClassifierElementIdAndSupplierClassifierElementId(UUID clientId, UUID supplierId);
}
