package com.casegen.collaboration;

import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class RealtimeEventPublisher {
    private final ApplicationEventPublisher events;

    public RealtimeEventPublisher(ApplicationEventPublisher events) {
        this.events = events;
    }

    public RealtimeEvent semantic(RealtimeEventType type, UUID projectId, UUID modelId,
            UUID entityId, long modelRevision) {
        return publish(type, projectId, modelId, null, entityId, modelRevision, null);
    }

    public RealtimeEvent diagram(RealtimeEventType type, UUID projectId, UUID modelId,
            UUID diagramId, UUID entityId, long diagramRevision) {
        return publish(type, projectId, modelId, diagramId, entityId, null, diagramRevision);
    }

    private RealtimeEvent publish(RealtimeEventType type, UUID projectId, UUID modelId,
            UUID diagramId, UUID entityId, Long modelRevision, Long diagramRevision) {
        RealtimeEvent event = new RealtimeEvent(UUID.randomUUID(), type, projectId, modelId,
                diagramId, entityId, modelRevision, diagramRevision, currentUserId(), Instant.now());
        events.publishEvent(event);
        return event;
    }

    private UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required");
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException exception) {
            throw new AuthenticationCredentialsNotFoundException("Invalid authenticated user", exception);
        }
    }
}
