package com.casegen.modeling.repository;

import com.casegen.modeling.domain.UmlModel;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface UmlModelRepository extends JpaRepository<UmlModel, UUID> {

    List<UmlModel> findByProjectIdOrderByNameAsc(UUID projectId);

    boolean existsByProjectIdAndName(UUID projectId, String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select model from UmlModel model where model.id = :modelId")
    Optional<UmlModel> findByIdForUpdate(@Param("modelId") UUID modelId);
}
