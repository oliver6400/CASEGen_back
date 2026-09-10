package com.casegen.modeling.repository;

import com.casegen.modeling.domain.UmlModel;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UmlModelRepository extends JpaRepository<UmlModel, UUID> {

    List<UmlModel> findByProjectIdOrderByNameAsc(UUID projectId);

    boolean existsByProjectIdAndName(UUID projectId, String name);
}
