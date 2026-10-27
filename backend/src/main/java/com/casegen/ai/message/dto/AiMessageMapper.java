package com.casegen.ai.message.dto;

import com.casegen.ai.message.domain.AiMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class AiMessageMapper {

    public AiMessageResponse toResponse(AiMessage message) {
        return new AiMessageResponse(
                message.getId(), message.getSession().getId(), message.getRole(), message.getSkill(),
                message.getAuthor() == null ? null : message.getAuthor().getId(),
                message.getAuthor() == null ? null : message.getAuthor().getDisplayName(),
                message.getContent(),
                message.getModel() == null ? null : message.getModel().getId(),
                message.getModel() == null ? null : message.getModel().getName(),
                message.getDiagram() == null ? null : message.getDiagram().getId(),
                message.getDiagram() == null ? null : message.getDiagram().getName(),
                message.getProvider(),
                message.getReplyTo() == null ? null : message.getReplyTo().getId(),
                message.getCreatedAt());
    }

    public PagedAiMessageResponse toPagedResponse(Page<AiMessage> page) {
        List<AiMessage> chronological = new ArrayList<>(page.getContent());
        Collections.reverse(chronological);
        return new PagedAiMessageResponse(chronological.stream().map(this::toResponse).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(),
                page.isFirst(), page.isLast());
    }
}
