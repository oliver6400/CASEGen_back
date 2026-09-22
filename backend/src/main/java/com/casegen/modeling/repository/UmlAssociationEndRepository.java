package com.casegen.modeling.repository;

import com.casegen.modeling.domain.UmlAssociationEnd;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UmlAssociationEndRepository extends JpaRepository<UmlAssociationEnd, UUID> {

    @EntityGraph(attributePaths = {
            "property", "property.element", "property.ownerClassifier", "property.typeClassifier", "association"
    })
    List<UmlAssociationEnd> findByAssociationElementIdOrderByMemberOrderAsc(UUID associationId);
}
