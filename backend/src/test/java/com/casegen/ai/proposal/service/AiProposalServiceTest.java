package com.casegen.ai.proposal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.casegen.ai.proposal.domain.AiProposal;
import com.casegen.ai.proposal.dto.CreateAiProposalRequest;
import com.casegen.ai.provider.AiProviderErrorCode;
import com.casegen.ai.provider.AiProviderException;
import com.casegen.auth.domain.User;
import com.casegen.project.service.ProjectAuthorizationService;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AiProposalServiceTest {
    private final ProjectAuthorizationService authorization = mock(ProjectAuthorizationService.class);
    private final AiProposalGenerator generator = mock(AiProposalGenerator.class);
    private final AiProposalPersistenceService persistence = mock(AiProposalPersistenceService.class);
    private final AiProposalService service = new AiProposalService(authorization, generator, persistence);
    private UUID projectId;
    private UUID sessionId;
    private UUID messageId;
    private UUID actorId;
    private UUID clientId;
    private User actor;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
        messageId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        clientId = UUID.randomUUID();
        actor = new User("member@test.local", "hash", "Member");
        ReflectionTestUtils.setField(actor, "id", actorId);
        when(authorization.requireActiveMemberOrSuperuser(projectId)).thenReturn(actor);
        when(persistence.projectExists(projectId)).thenReturn(true);
        when(persistence.findReplay(projectId, clientId)).thenReturn(Optional.empty());
    }

    @Test
    void createsPendingProposalUsingCurrentUserAndGeneratorOnce() {
        GeneratedAiProposal generated = mock(GeneratedAiProposal.class);
        AiProposal proposal = proposal(sessionId, messageId, actorId);
        when(generator.generate(any())).thenReturn(generated);
        when(persistence.persist(projectId, sessionId, messageId, actorId, clientId, generated))
                .thenReturn(proposal);

        CreateAiProposalResult result = service.create(projectId, sessionId, messageId,
                new CreateAiProposalRequest(clientId));

        assertThat(result.proposal()).isSameAs(proposal);
        assertThat(result.replayed()).isFalse();
        verify(authorization).requireActiveMemberOrSuperuser(projectId);
        verify(generator).generate(new GenerateAiProposalCommand(projectId, sessionId, messageId));
        verify(persistence).persist(projectId, sessionId, messageId, actorId, clientId, generated);
    }

    @Test
    void matchingReplayReturnsExistingWithoutCallingGenerator() {
        AiProposal existing = proposal(sessionId, messageId, actorId);
        when(persistence.findReplay(projectId, clientId)).thenReturn(Optional.of(existing));
        var result = service.create(projectId, sessionId, messageId,
                new CreateAiProposalRequest(clientId));
        assertThat(result.replayed()).isTrue();
        assertThat(result.proposal()).isSameAs(existing);
        verify(generator, never()).generate(any());
    }

    @Test
    void reusedClientIdForDifferentSourceOrRequesterIsConflict() {
        AiProposal existing = proposal(sessionId, UUID.randomUUID(), actorId);
        when(persistence.findReplay(projectId, clientId)).thenReturn(Optional.of(existing));
        assertCode(() -> service.create(projectId, sessionId, messageId,
                new CreateAiProposalRequest(clientId)), AiProposalErrorCode.CLIENT_PROPOSAL_ID_REUSED);
        verify(generator, never()).generate(any());
    }

    @Test
    void providerFailureDoesNotPersist() {
        when(generator.generate(any())).thenThrow(new AiProviderException(
                AiProviderErrorCode.TIMEOUT, "OLLAMA", "timeout"));
        assertThatThrownBy(() -> service.create(projectId, sessionId, messageId,
                new CreateAiProposalRequest(clientId))).isInstanceOf(AiProviderException.class);
        verify(persistence, never()).persist(any(), any(), any(), any(), any(), any());
    }

    @Test
    void identicalConcurrentRequestsCoalesceToOneGeneration() throws Exception {
        GeneratedAiProposal generated = mock(GeneratedAiProposal.class);
        AiProposal proposal = proposal(sessionId, messageId, actorId);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch bothAuthorized = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        when(authorization.requireActiveMemberOrSuperuser(projectId)).thenAnswer(invocation -> {
            bothAuthorized.countDown();
            return actor;
        });
        when(generator.generate(any())).thenAnswer(invocation -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return generated;
        });
        when(persistence.persist(projectId, sessionId, messageId, actorId, clientId, generated))
                .thenReturn(proposal);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> service.create(projectId, sessionId, messageId,
                    new CreateAiProposalRequest(clientId)));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> service.create(projectId, sessionId, messageId,
                    new CreateAiProposalRequest(clientId)));
            assertThat(bothAuthorized.await(5, TimeUnit.SECONDS)).isTrue();
            // Give the second request time to attach to the already registered in-flight
            // generation before releasing the provider call.
            Thread.sleep(100);
            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS).replayed()).isFalse();
            assertThat(second.get(5, TimeUnit.SECONDS).replayed()).isTrue();
        }
        verify(generator, times(1)).generate(any());
        verify(persistence, times(1)).persist(any(), any(), any(), any(), any(), any());
    }

    @Test
    void listAndDetailRequireInternalMembership() {
        service.list(projectId, null, null, null, null, null, 0, 30);
        service.detail(projectId, UUID.randomUUID());
        verify(authorization, times(2)).requireActiveMemberOrSuperuser(projectId);
    }

    @Test
    void invalidPaginationIsRejectedBeforeRepositoryQuery() {
        assertCode(() -> service.list(projectId, null, null, null, null, null, 0, 101),
                AiProposalErrorCode.INVALID_REQUEST);
        verify(persistence, never()).list(any(), any(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    private AiProposal proposal(UUID session, UUID message, UUID requester) {
        AiProposal value = mock(AiProposal.class);
        var sessionEntity = mock(com.casegen.ai.session.domain.AiSession.class);
        when(sessionEntity.getId()).thenReturn(session);
        var messageEntity = mock(com.casegen.ai.message.domain.AiMessage.class);
        when(messageEntity.getId()).thenReturn(message);
        var requesterEntity = mock(User.class);
        when(requesterEntity.getId()).thenReturn(requester);
        when(value.getSession()).thenReturn(sessionEntity);
        when(value.getSourceMessage()).thenReturn(messageEntity);
        when(value.getRequestedBy()).thenReturn(requesterEntity);
        return value;
    }

    private void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
            AiProposalErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(AiProposalException.class)
                .extracting(error -> ((AiProposalException) error).getCode()).isEqualTo(code);
    }
}
