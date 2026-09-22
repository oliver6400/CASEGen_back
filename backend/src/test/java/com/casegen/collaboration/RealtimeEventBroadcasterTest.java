package com.casegen.collaboration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

class RealtimeEventBroadcasterTest {

    @Test
    void publishesToModelAndDiagramTopicsOnlyAfterCommit() throws Exception {
        SimpMessagingTemplate messaging = Mockito.mock(SimpMessagingTemplate.class);
        CollaborationActivityService activities = Mockito.mock(CollaborationActivityService.class);
        RealtimeEventBroadcaster broadcaster = new RealtimeEventBroadcaster(messaging, activities);
        UUID modelId = UUID.randomUUID();
        UUID diagramId = UUID.randomUUID();
        RealtimeEvent event = new RealtimeEvent(UUID.randomUUID(), RealtimeEventType.DIAGRAM_NODE_MOVED,
                UUID.randomUUID(), modelId, diagramId, UUID.randomUUID(), null, 7L,
                UUID.randomUUID(), Instant.now());

        TransactionalEventListener listener = RealtimeEventBroadcaster.class
                .getMethod("broadcast", RealtimeEvent.class)
                .getAnnotation(TransactionalEventListener.class);
        assertEquals(TransactionPhase.AFTER_COMMIT, listener.phase());
        assertFalse(listener.fallbackExecution());

        broadcaster.broadcast(event);

        verify(activities).recordConfirmed(event);
        RealtimeEventMessage message = RealtimeEventMessage.from(event, null);
        verify(messaging).convertAndSend("/topic/models/" + modelId, message);
        verify(messaging).convertAndSend("/topic/diagrams/" + diagramId, message);
        verifyNoMoreInteractions(messaging);
    }

    @Test
    void rolledBackTransactionDoesNotBroadcast() {
        SimpMessagingTemplate messaging = Mockito.mock(SimpMessagingTemplate.class);
        CollaborationActivityService activities = Mockito.mock(CollaborationActivityService.class);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(SimpMessagingTemplate.class, () -> messaging);
            context.registerBean(CollaborationActivityService.class, () -> activities);
            context.registerBean(RealtimeEventBroadcaster.class);
            context.refresh();
            RealtimeEvent event = new RealtimeEvent(UUID.randomUUID(), RealtimeEventType.CLASSIFIER_CREATED,
                    UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(), 1L, null,
                    UUID.randomUUID(), Instant.now());
            TransactionTemplate transaction = new TransactionTemplate(testTransactionManager());

            transaction.executeWithoutResult(status -> {
                context.publishEvent(event);
                status.setRollbackOnly();
            });

            verifyNoInteractions(messaging);
            verifyNoInteractions(activities);
        }
    }

    private AbstractPlatformTransactionManager testTransactionManager() {
        return new AbstractPlatformTransactionManager() {
            @Override protected Object doGetTransaction() { return new Object(); }
            @Override protected void doBegin(Object transaction, TransactionDefinition definition) {}
            @Override protected void doCommit(DefaultTransactionStatus status) {}
            @Override protected void doRollback(DefaultTransactionStatus status) {}
        };
    }
}
