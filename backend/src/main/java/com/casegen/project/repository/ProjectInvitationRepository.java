package com.casegen.project.repository;

import com.casegen.project.domain.ProjectInvitation;
import com.casegen.project.domain.ProjectInvitationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectInvitationRepository extends JpaRepository<ProjectInvitation, UUID> {

    Optional<ProjectInvitation> findByTokenHash(String tokenHash);

    List<ProjectInvitation> findByEmailAndStatus(String email, ProjectInvitationStatus status);
}
