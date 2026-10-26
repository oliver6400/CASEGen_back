package com.casegen.ai.session.dto;

import com.casegen.ai.session.domain.AiSession;
import com.casegen.ai.session.repository.AiSessionHistoryRow;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class AiSessionMapper {

    public AiSessionResponse toResponse(AiSession session) {
        return new AiSessionResponse(
                session.getId(),
                session.getProject().getId(),
                session.getHost().getId(),
                session.getHost().getDisplayName(),
                session.getStatus(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                session.getParticipants().stream()
                        .map(participant -> new ParticipantResponse(
                                participant.getUser().getId(),
                                participant.getUser().getDisplayName(),
                                participant.getJoinedAt(),
                                participant.getLastSeenAt()))
                        .toList());
    }

    public AiSessionSummaryResponse toSummary(AiSessionHistoryRow row) {
        return new AiSessionSummaryResponse(
                row.getId(),
                row.getProjectId(),
                row.getHostUserId(),
                row.getHostName(),
                row.getStatus(),
                row.getStartedAt(),
                row.getEndedAt(),
                Math.toIntExact(row.getParticipantCount()));
    }

    public PagedAiSessionResponse toPagedResponse(Page<AiSessionHistoryRow> page) {
        return new PagedAiSessionResponse(
                page.getContent().stream().map(this::toSummary).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
