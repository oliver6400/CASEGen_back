package com.casegen.ai.message.realtime;

import com.casegen.ai.message.domain.AiMessage;
import com.casegen.ai.message.dto.AiMessageMapper;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class AiMessageEventPublisher {
    private final ApplicationEventPublisher events;
    private final AiMessageMapper mapper;
    private final Clock clock;

    public AiMessageEventPublisher(ApplicationEventPublisher events, AiMessageMapper mapper,
            Clock clock) {
        this.events = events;
        this.mapper = mapper;
        this.clock = clock;
    }

    public AiMessageEvent created(UUID projectId, AiMessage message) {
        AiMessageEvent event = new AiMessageEvent(UUID.randomUUID(),
                AiMessageEvent.MESSAGE_CREATED, projectId, message.getSession().getId(),
                mapper.toResponse(message), clock.instant());
        events.publishEvent(event);
        return event;
    }
}
