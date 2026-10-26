package com.casegen.ai.session.repository;

import com.casegen.ai.session.domain.AiSessionParticipant;
import com.casegen.ai.session.domain.AiSessionParticipantId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiSessionParticipantRepository
        extends JpaRepository<AiSessionParticipant, AiSessionParticipantId> {

    Optional<AiSessionParticipant> findBySession_IdAndUser_Id(UUID sessionId, UUID userId);

    boolean existsBySession_IdAndUser_Id(UUID sessionId, UUID userId);

    List<AiSessionParticipant> findBySession_IdOrderByJoinedAtAsc(UUID sessionId);
}
