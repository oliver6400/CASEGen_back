package com.casegen.project.repository;

import com.casegen.project.domain.ProjectMember;
import com.casegen.project.domain.ProjectMemberStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {

    List<ProjectMember> findByProjectId(UUID projectId);

    Optional<ProjectMember> findByProjectIdAndUserId(UUID projectId, UUID userId);

    Optional<ProjectMember> findByProjectIdAndUserIdAndStatus(
            UUID projectId, UUID userId, ProjectMemberStatus status);

    List<ProjectMember> findByUserIdAndStatusOrderByProjectNameAsc(
            UUID userId, ProjectMemberStatus status);
}
