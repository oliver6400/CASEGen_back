package com.casegen.ai.session.repository;

import com.casegen.ai.session.domain.AiSession;
import com.casegen.ai.session.domain.AiSessionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AiSessionRepository extends JpaRepository<AiSession, UUID> {

    @EntityGraph(attributePaths = {"host", "participants", "participants.user"})
    Optional<AiSession> findFirstByProjectIdAndStatus(UUID projectId, AiSessionStatus status);

    List<AiSession> findByProjectIdOrderByStartedAtDesc(UUID projectId);

    @Query(value = """
            select session.id as id,
                   session.project.id as projectId,
                   host.id as hostUserId,
                   host.displayName as hostName,
                   session.status as status,
                   session.startedAt as startedAt,
                   session.endedAt as endedAt,
                   count(participant) as participantCount
            from AiSession session
            join session.host host
            left join session.participants participant
            where session.project.id = :projectId
            group by session.id, session.project.id, host.id, host.displayName,
                     session.status, session.startedAt, session.endedAt
            order by session.startedAt desc
            """,
            countQuery = "select count(session) from AiSession session where session.project.id = :projectId")
    Page<AiSessionHistoryRow> findHistoryByProjectId(UUID projectId, Pageable pageable);

    @EntityGraph(attributePaths = {"host", "participants", "participants.user"})
    Optional<AiSession> findByIdAndProjectId(UUID id, UUID projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from AiSession session where session.id = :id and session.project.id = :projectId")
    Optional<AiSession> findByIdAndProjectIdForUpdate(UUID id, UUID projectId);
}
