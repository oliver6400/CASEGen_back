package com.casegen.modeling.repository;

import com.casegen.modeling.domain.Diagram;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiagramRepository extends JpaRepository<Diagram, UUID> {

    List<Diagram> findByModelIdOrderByNameAsc(UUID modelId);

    boolean existsByModelIdAndName(UUID modelId, String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select diagram from Diagram diagram where diagram.id = :diagramId")
    Optional<Diagram> findByIdForUpdate(@Param("diagramId") UUID diagramId);
}
