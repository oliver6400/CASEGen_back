package com.casegen.modeling.repository;

import com.casegen.modeling.domain.UmlElement;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UmlElementRepository extends JpaRepository<UmlElement, UUID> {
}
