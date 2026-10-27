package com.casegen.ai.message.realtime;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AiMessageBroadcaster {
    private final SimpMessagingTemplate messaging;

    public AiMessageBroadcaster(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void broadcast(AiMessageEvent event) {
        messaging.convertAndSend("/topic/projects/" + event.projectId() + "/ai", event);
    }
}
