package com.casegen.ai.proposal.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.casegen.ai.message.domain.AiSkill;
import com.casegen.ai.proposal.domain.AiProposalStatus;
import com.casegen.ai.proposal.domain.AiProposalType;
import com.casegen.ai.proposal.dto.AiProposalResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;
import tools.jackson.databind.json.JsonMapper;

class AiProposalRealtimeTest {
    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");

    @Test
    void publisherCreatesMinimalInternalEvent() {
        ApplicationEventPublisher spring = Mockito.mock(ApplicationEventPublisher.class);
        var publisher = new AiProposalEventPublisher(spring,
                Clock.fixed(NOW, ZoneOffset.UTC));
        UUID projectId = UUID.randomUUID();
        UUID proposalId = UUID.randomUUID();

        publisher.created(projectId, proposalId);

        var captured = ArgumentCaptor.forClass(AiProposalCreatedInternalEvent.class);
        verify(spring).publishEvent(captured.capture());
        assertThat(captured.getValue()).isEqualTo(
                new AiProposalCreatedInternalEvent(projectId, proposalId, NOW));
    }

    @Test
    void publisherCreatesAppliedAndRejectedInternalEventsWithDecisionTimestamp() {
        ApplicationEventPublisher spring = Mockito.mock(ApplicationEventPublisher.class);
        var publisher = new AiProposalEventPublisher(spring, Clock.fixed(NOW, ZoneOffset.UTC));
        UUID projectId = UUID.randomUUID();
        UUID proposalId = UUID.randomUUID();

        publisher.applied(projectId, proposalId, NOW);
        publisher.rejected(projectId, proposalId, NOW);

        verify(spring).publishEvent(
                new AiProposalAppliedInternalEvent(projectId, proposalId, NOW));
        verify(spring).publishEvent(
                new AiProposalRejectedInternalEvent(projectId, proposalId, NOW));
    }

    @Test
    void broadcasterReloadsCompleteResponseAndUsesExistingProjectAiTopic() throws Exception {
        AiProposalQueryService query = Mockito.mock(AiProposalQueryService.class);
        SimpMessagingTemplate messaging = Mockito.mock(SimpMessagingTemplate.class);
        var broadcaster = new AiProposalBroadcaster(query, messaging);
        UUID projectId = UUID.randomUUID();
        AiProposalResponse response = response(projectId);
        var internal = new AiProposalCreatedInternalEvent(projectId, response.id(), NOW);
        when(query.loadResponse(projectId, response.id())).thenReturn(response);

        var listener = AiProposalBroadcaster.class
                .getMethod("broadcast", AiProposalCreatedInternalEvent.class)
                .getAnnotation(TransactionalEventListener.class);
        assertThat(listener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
        assertThat(listener.fallbackExecution()).isFalse();

        broadcaster.broadcast(internal);

        var captured = ArgumentCaptor.forClass(AiProposalEvent.class);
        verify(messaging).convertAndSend(
                org.mockito.ArgumentMatchers.eq("/topic/projects/" + projectId + "/ai"),
                captured.capture());
        AiProposalEvent event = captured.getValue();
        assertThat(event.eventId()).isNotNull().isNotEqualTo(response.id());
        assertThat(event.eventType()).isEqualTo(AiProposalEvent.PROPOSAL_CREATED);
        assertThat(event.projectId()).isEqualTo(projectId);
        assertThat(event.proposal()).isEqualTo(response);
        assertThat(event.proposal().status()).isEqualTo(AiProposalStatus.PENDING);
        assertThat(event.proposal().provider()).isEqualTo("GEMINI");
        assertThat(event.proposal().skill()).isEqualTo(AiSkill.UML_REVIEWER);
        assertThat(event.proposal().requestedByName()).isEqualTo("Project member");
        assertThat(event.proposal().payload().get("name").asText()).isEqualTo("Invoice");
        assertThat(event.occurredAt()).isEqualTo(NOW);
    }

    @Test
    void rollbackPublishesNothing() {
        AiProposalQueryService query = Mockito.mock(AiProposalQueryService.class);
        SimpMessagingTemplate messaging = Mockito.mock(SimpMessagingTemplate.class);
        UUID projectId = UUID.randomUUID();
        AiProposalResponse response = response(projectId);
        var internal = new AiProposalCreatedInternalEvent(projectId, response.id(), NOW);
        when(query.loadResponse(projectId, response.id())).thenReturn(response);

        try (var context = context(query, messaging)) {
            TransactionSynchronizationManager.initSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try {
                context.publishEvent(internal);
                TransactionSynchronizationUtils.triggerAfterCompletion(
                        TransactionSynchronization.STATUS_ROLLED_BACK);
            } finally {
                TransactionSynchronizationManager.setActualTransactionActive(false);
                TransactionSynchronizationManager.clearSynchronization();
            }
        }

        verify(messaging, never()).convertAndSend(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.<Object>any());
    }

    @Test
    void appliedAndRejectedUseSameTopicAndDedicatedEventTypes() {
        AiProposalQueryService query = Mockito.mock(AiProposalQueryService.class);
        SimpMessagingTemplate messaging = Mockito.mock(SimpMessagingTemplate.class);
        var broadcaster = new AiProposalBroadcaster(query, messaging);
        UUID projectId = UUID.randomUUID();
        AiProposalResponse applied = decisionResponse(projectId, AiProposalStatus.APPLIED);
        AiProposalResponse rejected = decisionResponse(projectId, AiProposalStatus.REJECTED);
        when(query.loadResponse(projectId, applied.id())).thenReturn(applied);
        when(query.loadResponse(projectId, rejected.id())).thenReturn(rejected);

        broadcaster.broadcast(new AiProposalAppliedInternalEvent(projectId, applied.id(), NOW));
        broadcaster.broadcast(new AiProposalRejectedInternalEvent(projectId, rejected.id(), NOW));

        var captured = ArgumentCaptor.forClass(AiProposalEvent.class);
        verify(messaging, Mockito.times(2)).convertAndSend(
                org.mockito.ArgumentMatchers.eq("/topic/projects/" + projectId + "/ai"),
                captured.capture());
        assertThat(captured.getAllValues()).extracting(AiProposalEvent::eventType)
                .containsExactly(AiProposalEvent.PROPOSAL_APPLIED,
                        AiProposalEvent.PROPOSAL_REJECTED);
        assertThat(captured.getAllValues().get(0).proposal().status())
                .isEqualTo(AiProposalStatus.APPLIED);
        assertThat(captured.getAllValues().get(0).proposal().appliedElementId()).isNotNull();
        assertThat(captured.getAllValues().get(0).proposal().appliedModelRevision()).isEqualTo(4L);
        assertThat(captured.getAllValues().get(1).proposal().status())
                .isEqualTo(AiProposalStatus.REJECTED);
        assertThat(captured.getAllValues().get(1).proposal().appliedElementId()).isNull();
        assertThat(captured.getAllValues().get(1).proposal().appliedModelRevision()).isNull();
    }

    @Test
    void decisionEventsAreNotBroadcastOnRollback() {
        AiProposalQueryService query = Mockito.mock(AiProposalQueryService.class);
        SimpMessagingTemplate messaging = Mockito.mock(SimpMessagingTemplate.class);
        UUID projectId = UUID.randomUUID();
        UUID proposalId = UUID.randomUUID();

        try (var context = context(query, messaging)) {
            TransactionSynchronizationManager.initSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try {
                context.publishEvent(new AiProposalAppliedInternalEvent(projectId, proposalId, NOW));
                context.publishEvent(new AiProposalRejectedInternalEvent(projectId, proposalId, NOW));
                TransactionSynchronizationUtils.triggerAfterCompletion(
                        TransactionSynchronization.STATUS_ROLLED_BACK);
            } finally {
                TransactionSynchronizationManager.setActualTransactionActive(false);
                TransactionSynchronizationManager.clearSynchronization();
            }
        }

        verify(messaging, never()).convertAndSend(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.<Object>any());
    }

    @Test
    void postCommitBroadcastFailureIsContained() {
        AiProposalQueryService query = Mockito.mock(AiProposalQueryService.class);
        SimpMessagingTemplate messaging = Mockito.mock(SimpMessagingTemplate.class);
        UUID projectId = UUID.randomUUID();
        UUID proposalId = UUID.randomUUID();
        when(query.loadResponse(projectId, proposalId))
                .thenThrow(new IllegalStateException("controlled failure"));
        var broadcaster = new AiProposalBroadcaster(query, messaging);

        assertThatCode(() -> broadcaster.broadcast(
                new AiProposalCreatedInternalEvent(projectId, proposalId, NOW)))
                .doesNotThrowAnyException();
        verify(messaging, never()).convertAndSend(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.<Object>any());
    }

    private AnnotationConfigApplicationContext context(AiProposalQueryService query,
            SimpMessagingTemplate messaging) {
        var context = new AnnotationConfigApplicationContext();
        context.registerBean(AiProposalQueryService.class, () -> query);
        context.registerBean(SimpMessagingTemplate.class, () -> messaging);
        context.registerBean(AiProposalBroadcaster.class);
        context.refresh();
        return context;
    }

    private AiProposalResponse response(UUID projectId) {
        UUID modelId = UUID.randomUUID();
        UUID diagramId = UUID.randomUUID();
        return new AiProposalResponse(UUID.randomUUID(), null, projectId, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "Project member",
                AiProposalStatus.PENDING, AiProposalType.ADD_CLASSIFIER, "Add Invoice",
                "The model needs an invoice classifier",
                JsonMapper.builder().build().valueToTree(Map.of("name", "Invoice")),
                1, modelId, "Domain model", diagramId, "Class diagram", 3L, 5L,
                3L, 5L, false, false, null, null, null, null, null, null,
                "GEMINI", AiSkill.UML_REVIEWER, NOW, NOW);
    }

    private AiProposalResponse decisionResponse(UUID projectId, AiProposalStatus status) {
        UUID appliedElement = status == AiProposalStatus.APPLIED ? UUID.randomUUID() : null;
        Long appliedRevision = status == AiProposalStatus.APPLIED ? 4L : null;
        return new AiProposalResponse(UUID.randomUUID(), null, projectId, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "Project member", status,
                AiProposalType.ADD_CLASSIFIER, "Add Invoice", "Needed",
                JsonMapper.builder().build().valueToTree(Map.of("name", "Invoice")),
                1, UUID.randomUUID(), "Domain model", null, null, 3L, null,
                appliedRevision == null ? 3L : appliedRevision, null, false, false,
                UUID.randomUUID(), "Decision maker", NOW, "reviewed", appliedRevision,
                appliedElement, "GEMINI", AiSkill.UML_REVIEWER, NOW, NOW);
    }

}
