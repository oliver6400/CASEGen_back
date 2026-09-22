package com.casegen.modeling.repository;

import com.casegen.modeling.domain.ParameterDirection;
import com.casegen.modeling.domain.UmlParameter;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UmlParameterRepository extends JpaRepository<UmlParameter, UUID> {

    @EntityGraph(attributePaths = {"element", "operation", "typeClassifier"})
    List<UmlParameter> findByOperationElementIdOrderByPositionAsc(UUID operationId);

    boolean existsByOperationElementIdAndDirection(UUID operationId, ParameterDirection direction);

    boolean existsByOperationElementIdAndPosition(UUID operationId, int position);

    boolean existsByTypeClassifierElementId(UUID classifierId);
}
