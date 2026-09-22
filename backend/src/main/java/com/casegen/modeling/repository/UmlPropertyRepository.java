package com.casegen.modeling.repository;

import com.casegen.modeling.domain.UmlProperty;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UmlPropertyRepository extends JpaRepository<UmlProperty, UUID> {

    @EntityGraph(attributePaths = {"element", "ownerClassifier", "typeClassifier"})
    List<UmlProperty> findByOwnerClassifierElementIdOrderByElementNameAscElementIdAsc(UUID classifierId);

    boolean existsByTypeClassifierElementId(UUID classifierId);
}
