package com.casegen.modeling.repository;

import com.casegen.modeling.domain.UmlPackage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UmlPackageRepository extends JpaRepository<UmlPackage, UUID> {

    @EntityGraph(attributePaths = {"element", "parentPackage"})
    List<UmlPackage> findByElementModelIdOrderByElementNameAsc(UUID modelId);
}
