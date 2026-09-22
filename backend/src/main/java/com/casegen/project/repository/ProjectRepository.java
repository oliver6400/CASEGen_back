package com.casegen.project.repository;

import com.casegen.project.domain.Project;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.id = :id")
    Optional<Project> findByIdForUpdate(UUID id);

    @Query("select p from Project p where p.visibility = com.casegen.project.domain.ProjectVisibility.PUBLIC or exists (select m.id from ProjectMember m where m.project=p and m.user.id=:userId and m.status=com.casegen.project.domain.ProjectMemberStatus.ACTIVE) order by p.name,p.id")
    List<Project> findVisibleTo(UUID userId);
}
