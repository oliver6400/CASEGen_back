package com.casegen.ai.message.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.casegen.ai.context.AiContextBuilder;
import com.casegen.ai.context.AiContextRequest;
import com.casegen.ai.message.domain.AiMessage;
import com.casegen.ai.message.domain.AiSkill;
import com.casegen.ai.message.dto.SendAiMessageRequest;
import com.casegen.ai.message.exception.AiMessageErrorCode;
import com.casegen.ai.message.exception.AiMessageException;
import com.casegen.ai.provider.AiPrompt;
import com.casegen.ai.provider.AiPromptMessage;
import com.casegen.ai.provider.AiPromptRole;
import com.casegen.ai.provider.AiProviderErrorCode;
import com.casegen.ai.provider.AiProviderException;
import com.casegen.ai.provider.AiProviderProperties;
import com.casegen.ai.provider.AiProviderResult;
import com.casegen.ai.provider.SelectedAiProvider;
import com.casegen.ai.session.domain.AiSession;
import com.casegen.auth.domain.User;
import com.casegen.project.domain.Project;
import com.casegen.project.service.ProjectAuthorizationService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

class AiMessageServiceTest {
    private final UUID projectId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private ProjectAuthorizationService authorization;
    private AiContextBuilder contextBuilder;
    private SelectedAiProvider provider;
    private AiMessagePersistenceService persistence;
    private AiMessageService service;
    private User actor;
    private AiMessage user;
    private AiMessage assistant;
    private AiPrompt prompt;

    @BeforeEach
    void setUp() {
        authorization = org.mockito.Mockito.mock(ProjectAuthorizationService.class);
        contextBuilder = org.mockito.Mockito.mock(AiContextBuilder.class);
        provider = org.mockito.Mockito.mock(SelectedAiProvider.class);
        persistence = org.mockito.Mockito.mock(AiMessagePersistenceService.class);
        actor = user();
        AiSession session = session(actor);
        user = AiMessage.userMessage(session, actor, "question", null, null,
                UUID.randomUUID(), Instant.parse("2026-10-26T12:00:00Z"));
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        assistant = AiMessage.assistantMessage(session, "answer", "OLLAMA", null, null,
                user, Instant.parse("2026-10-26T12:00:01Z"));
        ReflectionTestUtils.setField(assistant, "id", UUID.randomUUID());
        prompt = new AiPrompt("system",
                List.of(new AiPromptMessage(AiPromptRole.USER, "question")));
        when(authorization.requireActiveMemberOrSuperuser(projectId)).thenReturn(actor);
        when(contextBuilder.build(any())).thenReturn(prompt);
        service = new AiMessageService(authorization, contextBuilder, provider, persistence,
                new AiSessionGenerationCoordinator(), properties());
    }

    @Test
    void newMessagePersistsUserCallsProviderAndPersistsAssistant() {
        var request = request(user.getClientMessageId(), " question ", null, null);
        when(persistence.persistOrReuseUser(projectId, sessionId, actor,
                request.clientMessageId(), "question", null, null, AiSkill.GENERAL))
                .thenReturn(new PreparedUserMessage(user, null, true));
        AiProviderResult result = new AiProviderResult("answer", "OLLAMA", "qwen3:4b", "stop");
        when(provider.generate(prompt)).thenReturn(result);
        when(persistence.persistAssistant(user.getId(), result)).thenReturn(assistant);

        SendAiMessageResult sent = service.send(projectId, sessionId, request);

        assertSame(user, sent.userMessage());
        assertSame(assistant, sent.assistantMessage());
        assertEquals(false, sent.replayed());
        verify(provider).generate(prompt);
        verify(persistence).persistAssistant(user.getId(), result);
        ArgumentCaptor<AiContextRequest> context = ArgumentCaptor.forClass(AiContextRequest.class);
        verify(contextBuilder).build(context.capture());
        assertEquals("question", context.getValue().currentUserContent());
        assertEquals(user.getId(), context.getValue().currentMessageId());
    }

    @Test
    void selectedSkillIsPersistedAndPassedToFreshContext() {
        var request = request(user.getClientMessageId(), "question", null, null,
                AiSkill.UML_REVIEWER);
        when(persistence.persistOrReuseUser(projectId, sessionId, actor,
                request.clientMessageId(), "question", null, null, AiSkill.UML_REVIEWER))
                .thenReturn(new PreparedUserMessage(user, null, true));
        AiProviderResult result = new AiProviderResult("answer", "OLLAMA", "model", "stop");
        when(provider.generate(prompt)).thenReturn(result);
        when(persistence.persistAssistant(user.getId(), result)).thenReturn(assistant);

        service.send(projectId, sessionId, request);

        ArgumentCaptor<AiContextRequest> context = ArgumentCaptor.forClass(AiContextRequest.class);
        verify(contextBuilder).build(context.capture());
        assertEquals(AiSkill.UML_REVIEWER, context.getValue().skill());
    }

    @Test
    void completedReplayDoesNotCallProvider() {
        var request = request(user.getClientMessageId(), "question", null, null);
        when(persistence.persistOrReuseUser(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PreparedUserMessage(user, assistant, false));

        SendAiMessageResult sent = service.send(projectId, sessionId, request);

        assertEquals(true, sent.replayed());
        assertSame(assistant, sent.assistantMessage());
        verify(provider, never()).generate(any());
    }

    @Test
    void retryWithoutAssistantExcludesPersistedUserAndCanComplete() {
        var request = request(user.getClientMessageId(), "question", null, null);
        when(persistence.persistOrReuseUser(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PreparedUserMessage(user, null, false));
        AiProviderResult result = new AiProviderResult("answer", "GEMINI", "model", "STOP");
        when(provider.generate(prompt)).thenReturn(result);
        when(persistence.persistAssistant(user.getId(), result)).thenReturn(assistant);

        assertEquals(true, service.send(projectId, sessionId, request).replayed());

        ArgumentCaptor<AiContextRequest> contexts = ArgumentCaptor.forClass(AiContextRequest.class);
        verify(contextBuilder).build(contexts.capture());
        assertEquals(user.getId(), contexts.getValue().currentMessageId());
    }

    @Test
    void providerFailurePropagatesAfterUserWasPersistedAndCreatesNoAssistant() {
        var request = request(user.getClientMessageId(), "question", null, null);
        when(persistence.persistOrReuseUser(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PreparedUserMessage(user, null, true));
        when(provider.generate(prompt)).thenThrow(new AiProviderException(
                AiProviderErrorCode.TIMEOUT, "OLLAMA", "internal endpoint detail"));

        AiProviderException thrown = assertThrows(AiProviderException.class,
                () -> service.send(projectId, sessionId, request));

        assertEquals(AiProviderErrorCode.TIMEOUT, thrown.getCode());
        verify(persistence, never()).persistAssistant(any(), any());
    }

    @Test
    void authorizationRunsBeforeContextOrPersistence() {
        when(authorization.requireActiveMemberOrSuperuser(projectId))
                .thenThrow(new AccessDeniedException("denied"));

        assertThrows(AccessDeniedException.class,
                () -> service.send(projectId, sessionId,
                        request(UUID.randomUUID(), "question", null, null)));
        verify(contextBuilder, never()).build(any());
        verify(persistence, never()).persistOrReuseUser(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsOversizedContentAndDiagramWithoutModel() {
        assertCode(AiMessageErrorCode.MESSAGE_TOO_LONG,
                request(UUID.randomUUID(), "x".repeat(101), null, null));
        assertCode(AiMessageErrorCode.MESSAGE_INVALID,
                request(UUID.randomUUID(), "question", null, UUID.randomUUID()));
    }

    @Test
    void concurrentIdenticalRequestsShareOneProviderCall() throws Exception {
        UUID clientId = user.getClientMessageId();
        var request = request(clientId, "question", null, null);
        when(persistence.persistOrReuseUser(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PreparedUserMessage(user, null, true));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AiProviderResult result = new AiProviderResult("answer", "OLLAMA", "model", "stop");
        when(provider.generate(prompt)).thenAnswer(invocation -> {
            entered.countDown();
            release.await(2, TimeUnit.SECONDS);
            return result;
        });
        when(persistence.persistAssistant(user.getId(), result)).thenReturn(assistant);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.send(projectId, sessionId, request));
            entered.await(2, TimeUnit.SECONDS);
            CountDownLatch secondStarted = new CountDownLatch(1);
            var second = executor.submit(() -> {
                secondStarted.countDown();
                return service.send(projectId, sessionId, request);
            });
            secondStarted.await(2, TimeUnit.SECONDS);
            Thread.sleep(100);
            release.countDown();
            assertSame(assistant, first.get(2, TimeUnit.SECONDS).assistantMessage());
            assertSame(assistant, second.get(2, TimeUnit.SECONDS).assistantMessage());
        }
        verify(provider, times(1)).generate(prompt);
        verify(persistence, times(1)).persistOrReuseUser(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void differentConcurrentPayloadForSameKeyIsConflict() throws Exception {
        UUID clientId = user.getClientMessageId();
        when(persistence.persistOrReuseUser(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PreparedUserMessage(user, null, true));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(provider.generate(prompt)).thenAnswer(invocation -> {
            entered.countDown(); release.await(2, TimeUnit.SECONDS);
            return new AiProviderResult("answer", "OLLAMA", "model", "stop");
        });
        when(persistence.persistAssistant(any(), any())).thenReturn(assistant);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(() -> service.send(projectId, sessionId,
                    request(clientId, "question", null, null)));
            entered.await(2, TimeUnit.SECONDS);
            AiMessageException conflict = assertThrows(AiMessageException.class,
                    () -> service.send(projectId, sessionId,
                            request(clientId, "different", null, null)));
            assertEquals(AiMessageErrorCode.CLIENT_MESSAGE_ID_REUSED, conflict.getCode());
            release.countDown();
            first.get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void differentMessagesPersistImmediatelyAndGenerateSeriallyWithFreshContext() throws Exception {
        UUID firstClientId = user.getClientMessageId();
        UUID secondClientId = UUID.randomUUID();
        AiMessage secondUser = AiMessage.userMessage(user.getSession(), actor, "question",
                null, null, secondClientId, Instant.parse("2026-10-26T12:00:02Z"));
        ReflectionTestUtils.setField(secondUser, "id", UUID.randomUUID());
        AiMessage secondAssistant = AiMessage.assistantMessage(user.getSession(), "answer 2",
                "OLLAMA", null, null, secondUser, Instant.parse("2026-10-26T12:00:03Z"));
        ReflectionTestUtils.setField(secondAssistant, "id", UUID.randomUUID());
        CountDownLatch usersPersisted = new CountDownLatch(2);
        CountDownLatch firstProviderEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        AtomicInteger providerCalls = new AtomicInteger();
        AtomicBoolean firstAssistantPersisted = new AtomicBoolean();

        when(persistence.persistOrReuseUser(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    UUID clientId = invocation.getArgument(3);
                    usersPersisted.countDown();
                    return new PreparedUserMessage(clientId.equals(firstClientId) ? user : secondUser,
                            null, true);
                });
        when(contextBuilder.build(any())).thenAnswer(invocation -> {
            AiContextRequest context = invocation.getArgument(0);
            if (context.currentMessageId().equals(secondUser.getId())) {
                assertTrue(firstAssistantPersisted.get());
            }
            return prompt;
        });
        when(provider.generate(prompt)).thenAnswer(invocation -> {
            int call = providerCalls.incrementAndGet();
            int current = active.incrementAndGet();
            maximum.accumulateAndGet(current, Math::max);
            if (call == 1) {
                firstProviderEntered.countDown();
                releaseFirst.await(2, TimeUnit.SECONDS);
            }
            active.decrementAndGet();
            return new AiProviderResult(call == 1 ? "answer" : "answer 2",
                    "OLLAMA", "model", "stop");
        });
        when(persistence.persistAssistant(any(), any())).thenAnswer(invocation -> {
            UUID userId = invocation.getArgument(0);
            if (userId.equals(user.getId())) {
                firstAssistantPersisted.set(true);
                return assistant;
            }
            return secondAssistant;
        });

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.send(projectId, sessionId,
                    request(firstClientId, "question", null, null)));
            assertTrue(firstProviderEntered.await(2, TimeUnit.SECONDS));
            var second = executor.submit(() -> service.send(projectId, sessionId,
                    request(secondClientId, "question", null, null)));
            assertTrue(usersPersisted.await(2, TimeUnit.SECONDS));
            assertEquals(1, maximum.get());
            releaseFirst.countDown();
            assertSame(assistant, first.get(2, TimeUnit.SECONDS).assistantMessage());
            assertSame(secondAssistant, second.get(2, TimeUnit.SECONDS).assistantMessage());
        }

        assertEquals(2, providerCalls.get());
        assertEquals(1, maximum.get());
        assertSame(user, assistant.getReplyTo());
        assertSame(secondUser, secondAssistant.getReplyTo());
        verify(persistence, times(2)).persistOrReuseUser(any(), any(), any(), any(), any(), any(), any(), any());
        verify(persistence, times(2)).persistAssistant(any(), any());
    }

    @Test
    void identicalContentFromDifferentAuthorsAndClientIdsRemainsIndependent() {
        User secondActor = user();
        UUID firstClientId = user.getClientMessageId();
        UUID secondClientId = UUID.randomUUID();
        AiMessage secondUser = AiMessage.userMessage(user.getSession(), secondActor, "question",
                null, null, secondClientId, Instant.parse("2026-10-26T12:00:02Z"));
        ReflectionTestUtils.setField(secondUser, "id", UUID.randomUUID());
        AiMessage secondAssistant = AiMessage.assistantMessage(user.getSession(), "answer 2",
                "OLLAMA", null, null, secondUser, Instant.parse("2026-10-26T12:00:03Z"));
        ReflectionTestUtils.setField(secondAssistant, "id", UUID.randomUUID());
        AiProviderResult result = new AiProviderResult("answer", "OLLAMA", "model", "stop");
        when(authorization.requireActiveMemberOrSuperuser(projectId)).thenReturn(actor, secondActor);
        when(persistence.persistOrReuseUser(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> new PreparedUserMessage(
                        invocation.getArgument(2) == actor ? user : secondUser, null, true));
        when(provider.generate(prompt)).thenReturn(result);
        when(persistence.persistAssistant(user.getId(), result)).thenReturn(assistant);
        when(persistence.persistAssistant(secondUser.getId(), result)).thenReturn(secondAssistant);

        SendAiMessageResult first = service.send(projectId, sessionId,
                request(firstClientId, "question", null, null));
        SendAiMessageResult second = service.send(projectId, sessionId,
                request(secondClientId, "question", null, null));

        assertSame(actor, first.userMessage().getAuthor());
        assertSame(secondActor, second.userMessage().getAuthor());
        assertSame(user, first.assistantMessage().getReplyTo());
        assertSame(secondUser, second.assistantMessage().getReplyTo());
        verify(provider, times(2)).generate(prompt);
        verify(persistence).persistOrReuseUser(projectId, sessionId, actor,
                firstClientId, "question", null, null, AiSkill.GENERAL);
        verify(persistence).persistOrReuseUser(projectId, sessionId, secondActor,
                secondClientId, "question", null, null, AiSkill.GENERAL);
    }

    private void assertCode(AiMessageErrorCode code, SendAiMessageRequest request) {
        assertEquals(code, assertThrows(AiMessageException.class,
                () -> service.send(projectId, sessionId, request)).getCode());
    }

    private SendAiMessageRequest request(UUID id, String content, UUID modelId, UUID diagramId) {
        return new SendAiMessageRequest(id, content, modelId, diagramId);
    }

    private SendAiMessageRequest request(UUID id, String content, UUID modelId, UUID diagramId,
            AiSkill skill) {
        return new SendAiMessageRequest(id, content, modelId, diagramId, skill);
    }

    private User user() {
        User value = new User("member@test.local", "hash", "Member");
        ReflectionTestUtils.setField(value, "id", UUID.randomUUID());
        return value;
    }

    private AiSession session(User host) {
        Project project = new Project("Project", null);
        ReflectionTestUtils.setField(project, "id", projectId);
        AiSession value = new AiSession(project, host, Instant.parse("2026-10-26T10:00:00Z"));
        ReflectionTestUtils.setField(value, "id", sessionId);
        return value;
    }

    private AiProviderProperties properties() {
        return new AiProviderProperties(AiProviderProperties.Provider.OLLAMA, 100, 30, 24000,
                new AiProviderProperties.Ollama("http://localhost", "model", Duration.ofSeconds(1)),
                new AiProviderProperties.Gemini("", "model", "http://localhost", Duration.ofSeconds(1)));
    }
}
