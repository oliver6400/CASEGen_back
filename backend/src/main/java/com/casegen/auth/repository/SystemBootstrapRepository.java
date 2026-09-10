package com.casegen.auth.repository;

import com.casegen.auth.domain.SystemBootstrap;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SystemBootstrapRepository extends JpaRepository<SystemBootstrap, Short> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select bootstrap from SystemBootstrap bootstrap where bootstrap.id = :id")
    Optional<SystemBootstrap> findByIdForUpdate(@Param("id") short id);
}
