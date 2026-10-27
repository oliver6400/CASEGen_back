package com.casegen.ai.message.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.casegen.ai.message.domain.AiMessage;
import com.casegen.ai.message.domain.AiMessageRole;
import com.casegen.ai.message.domain.AiSkill;
import com.casegen.ai.message.exception.AiMessageErrorCode;
import com.casegen.ai.message.exception.AiMessageException;
import com.casegen.ai.message.realtime.AiMessageEventPublisher;
import com.casegen.ai.message.repository.AiMessageRepository;
import com.casegen.ai.provider.AiProviderResult;
import com.casegen.ai.session.domain.AiSession;
import com.casegen.ai.session.repository.AiSessionRepository;
import com.casegen.auth.domain.User;
import com.casegen.modeling.repository.DiagramRepository;
import com.casegen.modeling.repository.UmlModelRepository;
import com.casegen.project.domain.Project;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class AiMessagePersistenceServiceTest {
    private final UUID projectId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();
    private AiMessageRepository messages;
    private AiSessionRepository sessions;
    private AiMessageEventPublisher events;
    private AiMessagePersistenceService persistence;
    private User actor;
    private AiSession session;
    private AiMessage user;
    private AiMessage assistant;

    @BeforeEach
    void setUp() {
        messages = Mockito.mock(AiMessageRepository.class);
        sessions = Mockito.mock(AiSessionRepository.class);
        events = Mockito.mock(AiMessageEventPublisher.class);
        actor = new User("member@test.local", "hash", "Member");
        ReflectionTestUtils.setField(actor, "id", UUID.randomUUID());
        Project project = new Project("Project", null);
        ReflectionTestUtils.setField(project, "id", projectId);
        session = new AiSession(project, actor, Instant.parse("2026-10-26T10:00:00Z"));
        ReflectionTestUtils.setField(session, "id", sessionId);
        user = AiMessage.userMessage(session, actor, "question", null, null, clientId,
                Instant.parse("2026-10-26T10:01:00Z"));
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        assistant = AiMessage.assistantMessage(session, "answer", "OLLAMA", null, null, user,
                Instant.parse("2026-10-26T10:01:01Z"));
        ReflectionTestUtils.setField(assistant, "id", UUID.randomUUID());
        persistence = new AiMessagePersistenceService(messages, sessions,
                Mockito.mock(UmlModelRepository.class), Mockito.mock(DiagramRepository.class),
                Clock.fixed(Instant.parse("2026-10-26T10:01:00Z"), ZoneOffset.UTC), events);
    }

    @Test
    void newUserPublishesExactlyOneTransactionalEvent() {
        when(sessions.findByIdAndProjectIdForUpdate(sessionId, projectId)).thenReturn(Optional.of(session));
        when(messages.findBySession_IdAndClientMessageId(sessionId, clientId)).thenReturn(Optional.empty());
        when(messages.saveAndFlush(any(AiMessage.class))).thenAnswer(invocation -> {
            AiMessage saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });

        PreparedUserMessage result = persistence.persistOrReuseUser(projectId, sessionId, actor,
                clientId, "question", null, null);

        assertTrue(result.created());
        verify(events).created(projectId, result.userMessage());
    }

    @ParameterizedTest
    @EnumSource(value = AiSkill.class, names = {"UML_REVIEWER", "DATABASE_REVIEWER"})
    void newUserPersistsSelectedSkill(AiSkill skill) {
        when(sessions.findByIdAndProjectIdForUpdate(sessionId, projectId)).thenReturn(Optional.of(session));
        when(messages.findBySession_IdAndClientMessageId(sessionId, clientId)).thenReturn(Optional.empty());
        when(messages.saveAndFlush(any(AiMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PreparedUserMessage result = persistence.persistOrReuseUser(projectId, sessionId, actor,
                clientId, "question", null, null, skill);

        assertEquals(skill, result.userMessage().getSkill());
    }

    @Test
    void sameClientMessageWithDifferentSkillIsConflict() {
        when(sessions.findByIdAndProjectIdForUpdate(sessionId, projectId)).thenReturn(Optional.of(session));
        when(messages.findBySession_IdAndClientMessageId(sessionId, clientId))
                .thenReturn(Optional.of(user));

        AiMessageException error = assertThrows(AiMessageException.class,
                () -> persistence.persistOrReuseUser(projectId, sessionId, actor,
                        clientId, "question", null, null, AiSkill.UML_REVIEWER));

        assertEquals(AiMessageErrorCode.CLIENT_MESSAGE_ID_REUSED, error.getCode());
    }

    @Test
    void completedReplayPublishesNeitherUserNorAssistant() {
        when(sessions.findByIdAndProjectIdForUpdate(sessionId, projectId)).thenReturn(Optional.of(session));
        when(messages.findBySession_IdAndClientMessageId(sessionId, clientId))
                .thenReturn(Optional.of(user));
        when(messages.findByReplyTo_IdAndRole(user.getId(), AiMessageRole.ASSISTANT))
                .thenReturn(Optional.of(assistant));

        PreparedUserMessage result = persistence.persistOrReuseUser(projectId, sessionId, actor,
                clientId, "question", null, null);

        assertFalse(result.created());
        assertSame(assistant, result.assistantMessage());
        verify(events, never()).created(any(), any());
    }

    @Test
    void retryWithExistingUserDoesNotRepublishUser() {
        when(sessions.findByIdAndProjectIdForUpdate(sessionId, projectId)).thenReturn(Optional.of(session));
        when(messages.findBySession_IdAndClientMessageId(sessionId, clientId))
                .thenReturn(Optional.of(user));
        when(messages.findByReplyTo_IdAndRole(user.getId(), AiMessageRole.ASSISTANT))
                .thenReturn(Optional.empty());

        PreparedUserMessage result = persistence.persistOrReuseUser(projectId, sessionId, actor,
                clientId, "question", null, null);

        assertFalse(result.created());
        verify(events, never()).created(any(), any());
    }

    @Test
    void successfulRetryPublishesOnlyNewAssistant() {
        AiProviderResult provider = new AiProviderResult("answer", "OLLAMA", "model", "stop");
        when(messages.findDetailedById(user.getId())).thenReturn(Optional.of(user));
        when(messages.findByReplyTo_IdAndRole(user.getId(), AiMessageRole.ASSISTANT))
                .thenReturn(Optional.empty());
        when(messages.saveAndFlush(any(AiMessage.class))).thenReturn(assistant);

        AiMessage result = persistence.persistAssistant(user.getId(), provider);

        assertSame(assistant, result);
        verify(events).created(projectId, assistant);
    }
}
