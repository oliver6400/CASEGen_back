package com.casegen.modeling.repository;

import com.casegen.modeling.domain.UmlAssociation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UmlAssociationRepository extends JpaRepository<UmlAssociation, UUID> {

    @EntityGraph(attributePaths = {"element", "umlPackage"})
    List<UmlAssociation> findByElementModelIdOrderByElementCreatedAtAsc(UUID modelId);
}
