package com.casegen.auth.repository;

import com.casegen.auth.domain.User;
import com.casegen.auth.domain.SystemRole;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    long countBySystemRole(SystemRole systemRole);
}
