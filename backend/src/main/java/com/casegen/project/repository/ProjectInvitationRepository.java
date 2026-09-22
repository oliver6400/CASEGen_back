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
    List<ProjectInvitation> findByProjectIdOrderByCreatedAtDesc(UUID projectId);
    List<ProjectInvitation> findByEmailIgnoreCaseAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(String email, ProjectInvitationStatus status, java.time.Instant now);
    boolean existsByProjectIdAndEmailIgnoreCaseAndStatusAndExpiresAtAfter(UUID projectId, String email, ProjectInvitationStatus status, java.time.Instant now);
    @org.springframework.data.jpa.repository.Query("select i.project.id from ProjectInvitation i where i.id=:id")
    Optional<UUID> findProjectIdByInvitationId(UUID id);
}
