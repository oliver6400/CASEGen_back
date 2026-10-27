package com.casegen.ai.message.repository;

import com.casegen.ai.message.domain.AiMessage;
import com.casegen.ai.message.domain.AiMessageRole;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AiMessageRepository extends JpaRepository<AiMessage, UUID> {

    @EntityGraph(attributePaths = {"author", "model", "diagram", "session", "replyTo"})
    Page<AiMessage> findBySession_IdOrderByCreatedAtDescIdDesc(UUID sessionId, Pageable pageable);

    @EntityGraph(attributePaths = {"author", "model", "diagram", "session", "replyTo"})
    Page<AiMessage> findBySession_IdOrderByCreatedAtAscIdAsc(UUID sessionId, Pageable pageable);

    @EntityGraph(attributePaths = {"author", "model", "diagram", "session", "replyTo"})
    Optional<AiMessage> findBySession_IdAndClientMessageId(UUID sessionId, UUID clientMessageId);

    @EntityGraph(attributePaths = {"author", "model", "diagram", "session", "replyTo"})
    Optional<AiMessage> findByReplyTo_IdAndRole(UUID replyToMessageId, AiMessageRole role);

    @EntityGraph(attributePaths = {"author", "model", "diagram", "session", "replyTo"})
    @Query("select message from AiMessage message where message.id = :id")
    Optional<AiMessage> findDetailedById(UUID id);

    boolean existsBySession_IdAndClientMessageId(UUID sessionId, UUID clientMessageId);

    boolean existsByReplyTo_IdAndRole(UUID replyToMessageId, AiMessageRole role);

    @Query("""
            select message from AiMessage message
            where message.session.project.id = :projectId
            order by message.createdAt desc, message.id desc
            """)
    java.util.List<AiMessage> findRecentByProjectId(UUID projectId, Pageable pageable);

    @Query("""
            select message from AiMessage message
            where message.session.project.id = :projectId and message.id <> :excludedMessageId
            order by message.createdAt desc, message.id desc
            """)
    java.util.List<AiMessage> findRecentByProjectIdExcludingMessage(
            UUID projectId, UUID excludedMessageId, Pageable pageable);
}
