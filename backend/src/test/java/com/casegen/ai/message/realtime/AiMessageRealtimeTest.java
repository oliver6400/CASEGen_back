package com.casegen.ai.message.realtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.casegen.ai.message.domain.AiMessage;
import com.casegen.ai.message.domain.AiSkill;
import com.casegen.ai.message.dto.AiMessageMapper;
import com.casegen.ai.session.domain.AiSession;
import com.casegen.auth.domain.User;
import com.casegen.project.domain.Project;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class AiMessageRealtimeTest {
    private static final Instant NOW = Instant.parse("2026-10-26T15:00:00Z");

    @Test
    void publisherCreatesDedicatedSafeEvent() {
        ApplicationEventPublisher spring = Mockito.mock(ApplicationEventPublisher.class);
        AiMessageEventPublisher publisher = new AiMessageEventPublisher(
                spring, new AiMessageMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
        Fixture fixture = fixture();

        publisher.created(fixture.projectId(), fixture.userMessage());

        ArgumentCaptor<AiMessageEvent> captured = ArgumentCaptor.forClass(AiMessageEvent.class);
        verify(spring).publishEvent(captured.capture());
        AiMessageEvent event = captured.getValue();
        assertEquals(AiMessageEvent.MESSAGE_CREATED, event.eventType());
        assertEquals(fixture.projectId(), event.projectId());
        assertEquals(fixture.sessionId(), event.sessionId());
        assertEquals(fixture.userMessage().getId(), event.message().id());
        assertEquals(AiSkill.UML_REVIEWER, event.message().skill());
        assertEquals(NOW, event.occurredAt());
    }

    @Test
    void broadcasterUsesOnlyProjectAiTopicAndIsAfterCommit() throws Exception {
        SimpMessagingTemplate messaging = Mockito.mock(SimpMessagingTemplate.class);
        AiMessageBroadcaster broadcaster = new AiMessageBroadcaster(messaging);
        Fixture fixture = fixture();
        AiMessageEvent event = event(fixture, fixture.userMessage());

        TransactionalEventListener listener = AiMessageBroadcaster.class
                .getMethod("broadcast", AiMessageEvent.class)
                .getAnnotation(TransactionalEventListener.class);
        assertEquals(TransactionPhase.AFTER_COMMIT, listener.phase());
        assertFalse(listener.fallbackExecution());

        broadcaster.broadcast(event);

        verify(messaging).convertAndSend(
                "/topic/projects/" + fixture.projectId() + "/ai", event);
    }

    @Test
    void committedUserAndAssistantAreBroadcastInOrderAndRollbackPublishesNothing() {
        SimpMessagingTemplate messaging = Mockito.mock(SimpMessagingTemplate.class);
        Fixture fixture = fixture();
        AiMessageEvent user = event(fixture, fixture.userMessage());
        AiMessageEvent assistant = event(fixture, fixture.assistantMessage());
        AiMessageBroadcaster broadcaster = new AiMessageBroadcaster(messaging);

        broadcaster.broadcast(user);
        broadcaster.broadcast(assistant);

        var ordered = inOrder(messaging);
        ordered.verify(messaging).convertAndSend(
                "/topic/projects/" + fixture.projectId() + "/ai", user);
        ordered.verify(messaging).convertAndSend(
                "/topic/projects/" + fixture.projectId() + "/ai", assistant);

        SimpMessagingTemplate rolledBack = Mockito.mock(SimpMessagingTemplate.class);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(SimpMessagingTemplate.class, () -> rolledBack);
            context.registerBean(AiMessageBroadcaster.class);
            context.refresh();
            new TransactionTemplate(testTransactionManager()).executeWithoutResult(status -> {
                context.publishEvent(user);
                status.setRollbackOnly();
            });
        }
        verifyNoInteractions(rolledBack);
    }

    private AiMessageEvent event(Fixture fixture, AiMessage message) {
        return new AiMessageEvent(UUID.randomUUID(), AiMessageEvent.MESSAGE_CREATED,
                fixture.projectId(), fixture.sessionId(), new AiMessageMapper().toResponse(message), NOW);
    }

    private Fixture fixture() {
        UUID projectId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        User author = new User("private@test.local", "hash", "Member");
        ReflectionTestUtils.setField(author, "id", UUID.randomUUID());
        Project project = new Project("Project", null);
        ReflectionTestUtils.setField(project, "id", projectId);
        AiSession session = new AiSession(project, author, NOW.minusSeconds(60));
        ReflectionTestUtils.setField(session, "id", sessionId);
        AiMessage user = AiMessage.userMessage(session, author, "question", null, null,
                UUID.randomUUID(), AiSkill.UML_REVIEWER, NOW);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        AiMessage assistant = AiMessage.assistantMessage(session, "answer", "OLLAMA", null,
                null, user, NOW.plusSeconds(1));
        ReflectionTestUtils.setField(assistant, "id", UUID.randomUUID());
        return new Fixture(projectId, sessionId, user, assistant);
    }

    private AbstractPlatformTransactionManager testTransactionManager() {
        return new AbstractPlatformTransactionManager() {
            @Override protected Object doGetTransaction() { return new Object(); }
            @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
            @Override protected void doCommit(DefaultTransactionStatus status) { }
            @Override protected void doRollback(DefaultTransactionStatus status) { }
        };
    }

    private record Fixture(UUID projectId, UUID sessionId, AiMessage userMessage,
                           AiMessage assistantMessage) { }
}
