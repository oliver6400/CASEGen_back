package com.casegen.collaboration;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class RealtimeEventBroadcaster {
    private static final Logger LOGGER = LoggerFactory.getLogger(RealtimeEventBroadcaster.class);
    private final SimpMessagingTemplate messaging;
    private final CollaborationActivityService activities;

    public RealtimeEventBroadcaster(SimpMessagingTemplate messaging, CollaborationActivityService activities) {
        this.messaging = messaging;
        this.activities = activities;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void broadcast(RealtimeEvent event) {
        CollaborationActivityResponse activity = null;
        try {
            activity = activities.recordConfirmed(event);
        } catch (RuntimeException exception) {
            LOGGER.error("Could not persist collaboration activity for event {}", event.eventId(), exception);
        }
        RealtimeEventMessage message = RealtimeEventMessage.from(event, activity);
        messaging.convertAndSend("/topic/models/" + event.modelId(), message);
        if (event.diagramId() != null) {
            messaging.convertAndSend("/topic/diagrams/" + event.diagramId(), message);
        }
    }
}
