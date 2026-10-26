package com.casegen.ai.session.service;

import static com.casegen.ai.session.exception.AiSessionErrorCode.AI_SESSION_CONCURRENT_START;
import static com.casegen.ai.session.exception.AiSessionErrorCode.SESSION_NOT_ACTIVE;
import static com.casegen.ai.session.exception.AiSessionErrorCode.SESSION_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.casegen.ai.session.domain.AiSession;
import com.casegen.ai.session.domain.AiSessionParticipant;
import com.casegen.ai.session.domain.AiSessionStatus;
import com.casegen.ai.session.exception.AiSessionException;
import com.casegen.ai.session.repository.AiSessionParticipantRepository;
import com.casegen.ai.session.repository.AiSessionHistoryRow;
import com.casegen.ai.session.repository.AiSessionRepository;
import com.casegen.auth.domain.User;
import com.casegen.project.domain.Project;
import com.casegen.project.repository.ProjectRepository;
import com.casegen.project.service.ProjectAuthorizationService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class AiSessionServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-25T15:00:00Z");

    private final AiSessionRepository sessions = Mockito.mock(AiSessionRepository.class);
    private final AiSessionParticipantRepository participants = Mockito.mock(AiSessionParticipantRepository.class);
    private final ProjectRepository projects = Mockito.mock(ProjectRepository.class);
    private final ProjectAuthorizationService authorization = Mockito.mock(ProjectAuthorizationService.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final UUID projectId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private Project project;
    private User actor;
    private AiSessionService service;

    @BeforeEach
    void setUp() {
        project = project(projectId);
        actor = user(UUID.randomUUID(), "Actor");
        service = new AiSessionService(sessions, participants, projects, authorization, clock);
        when(authorization.requireWrite(projectId)).thenReturn(actor);
        when(authorization.requireActiveMemberOrSuperuser(projectId)).thenReturn(actor);
        when(projects.findByIdForUpdate(projectId)).thenReturn(Optional.of(project));
        when(sessions.saveAndFlush(any(AiSession.class))).thenAnswer(invocation -> {
            AiSession value = invocation.getArgument(0);
            ReflectionTestUtils.setField(value, "id", UUID.randomUUID());
            return value;
        });
    }

    @Test
    void startCreatesActiveSessionAndRegistersHost() {
        when(sessions.findFirstByProjectIdAndStatus(projectId, AiSessionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        var result = service.startOrReuse(projectId);

        assertTrue(result.created());
        assertTrue(result.session().isActive());
        assertSame(actor, result.session().getHost());
        assertEquals(NOW, result.session().getStartedAt());
        verify(projects).findByIdForUpdate(projectId);
        verify(sessions).saveAndFlush(result.session());
        ArgumentCaptor<AiSessionParticipant> saved = ArgumentCaptor.forClass(AiSessionParticipant.class);
        verify(participants).save(saved.capture());
        assertSame(actor, saved.getValue().getUser());
        assertEquals(NOW, saved.getValue().getJoinedAt());
        assertEquals(1, result.session().getParticipants().size());
    }

    @Test
    void startReusesActiveSessionAndAddsNewParticipant() {
        AiSession active = session(true);
        when(sessions.findFirstByProjectIdAndStatus(projectId, AiSessionStatus.ACTIVE))
                .thenReturn(Optional.of(active));
        when(participants.findBySession_IdAndUser_Id(sessionId, actor.getId()))
                .thenReturn(Optional.empty());

        var result = service.startOrReuse(projectId);

        assertFalse(result.created());
        assertSame(active, result.session());
        verify(sessions, never()).saveAndFlush(any());
        verify(participants).save(any(AiSessionParticipant.class));
    }

    @Test
    void startReuseTouchesExistingParticipantWithoutChangingJoinDate() {
        AiSession active = session(true);
        Instant joined = NOW.minusSeconds(600);
        AiSessionParticipant participant = new AiSessionParticipant(active, actor, joined);
        when(sessions.findFirstByProjectIdAndStatus(projectId, AiSessionStatus.ACTIVE))
                .thenReturn(Optional.of(active));
        when(participants.findBySession_IdAndUser_Id(sessionId, actor.getId()))
                .thenReturn(Optional.of(participant));

        service.startOrReuse(projectId);

        assertEquals(joined, participant.getJoinedAt());
        assertEquals(NOW, participant.getLastSeenAt());
        verify(participants, never()).save(any());
    }

    @Test
    void currentReturnsOptionalWithoutRegisteringParticipant() {
        AiSession active = session(true);
        when(sessions.findFirstByProjectIdAndStatus(projectId, AiSessionStatus.ACTIVE))
                .thenReturn(Optional.of(active));

        assertSame(active, service.getCurrent(projectId).orElseThrow());
        verify(participants, never()).save(any());
        verify(participants, never()).findBySession_IdAndUser_Id(any(), any());
    }

    @Test
    void currentCanBeEmpty() {
        when(sessions.findFirstByProjectIdAndStatus(projectId, AiSessionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertTrue(service.getCurrent(projectId).isEmpty());
    }

    @Test
    void internalAuthorizationFailureIsPropagated() {
        var denied = new org.springframework.security.access.AccessDeniedException("denied");
        when(authorization.requireActiveMemberOrSuperuser(projectId)).thenThrow(denied);

        assertSame(denied, assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.getCurrent(projectId)));
        verify(sessions, never()).findFirstByProjectIdAndStatus(any(), any());
    }

    @Test
    void getSessionRequiresMatchingProjectAndSession() {
        when(sessions.findByIdAndProjectId(sessionId, projectId)).thenReturn(Optional.empty());

        AiSessionException error = assertThrows(AiSessionException.class,
                () -> service.getSession(projectId, sessionId));

        assertEquals(SESSION_NOT_FOUND, error.getCode());
    }

    @Test
    void joinAddsParticipantAndRepeatedJoinOnlyTouchesIt() {
        AiSession active = session(true);
        when(sessions.findByIdAndProjectIdForUpdate(sessionId, projectId))
                .thenReturn(Optional.of(active));
        when(sessions.findByIdAndProjectId(sessionId, projectId)).thenReturn(Optional.of(active));
        when(participants.findBySession_IdAndUser_Id(sessionId, actor.getId()))
                .thenReturn(Optional.empty());
        service.join(projectId, sessionId);
        ArgumentCaptor<AiSessionParticipant> saved = ArgumentCaptor.forClass(AiSessionParticipant.class);
        verify(participants).save(saved.capture());
        AiSessionParticipant participant = saved.getValue();
        Instant joined = participant.getJoinedAt();

        participant.touch(NOW.minusSeconds(30));
        when(participants.findBySession_IdAndUser_Id(sessionId, actor.getId()))
                .thenReturn(Optional.of(participant));
        service.join(projectId, sessionId);

        assertEquals(joined, participant.getJoinedAt());
        assertEquals(NOW, participant.getLastSeenAt());
    }

    @Test
    void joinRejectsClosedSession() {
        AiSession closed = session(false);
        when(sessions.findByIdAndProjectIdForUpdate(sessionId, projectId))
                .thenReturn(Optional.of(closed));

        AiSessionException error = assertThrows(AiSessionException.class,
                () -> service.join(projectId, sessionId));

        assertEquals(SESSION_NOT_ACTIVE, error.getCode());
    }

    @Test
    void endClosesSessionAndIsIdempotentWithoutRemovingParticipants() {
        AiSession active = session(true);
        AiSessionParticipant participant = new AiSessionParticipant(active, actor, NOW.minusSeconds(60));
        active.addParticipant(participant);
        when(sessions.findByIdAndProjectIdForUpdate(sessionId, projectId))
                .thenReturn(Optional.of(active));
        when(sessions.findByIdAndProjectId(sessionId, projectId)).thenReturn(Optional.of(active));

        service.end(projectId, sessionId);
        service.end(projectId, sessionId);

        assertFalse(active.isActive());
        assertEquals(NOW, active.getEndedAt());
        assertEquals(1, active.getParticipants().size());
    }

    @Test
    void endRejectsSessionFromAnotherProject() {
        when(sessions.findByIdAndProjectIdForUpdate(sessionId, projectId))
                .thenReturn(Optional.empty());

        AiSessionException error = assertThrows(AiSessionException.class,
                () -> service.end(projectId, sessionId));

        assertEquals(SESSION_NOT_FOUND, error.getCode());
    }

    @Test
    void historyUsesDescendingRepositoryQueryAndRequestedPage() {
        var pageable = PageRequest.of(1, 20);
        AiSessionHistoryRow row = Mockito.mock(AiSessionHistoryRow.class);
        var expected = new PageImpl<>(List.of(row), pageable, 21);
        when(sessions.findHistoryByProjectId(projectId, pageable)).thenReturn(expected);

        assertSame(expected, service.history(projectId, pageable));
        verify(sessions).findHistoryByProjectId(projectId, pageable);
    }

    @Test
    void historyLimitsUnboundedAndOversizedRequests() {
        when(sessions.findHistoryByProjectId(any(), any())).thenReturn(Page.empty());

        service.history(projectId, Pageable.unpaged());
        service.history(projectId, PageRequest.of(2, 500));

        verify(sessions).findHistoryByProjectId(projectId, PageRequest.of(0, 100));
        verify(sessions).findHistoryByProjectId(projectId, PageRequest.of(2, 100));
    }

    @Test
    void activeSessionUniqueViolationGetsDomainCode() {
        when(sessions.findFirstByProjectIdAndStatus(projectId, AiSessionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        var cause = new RuntimeException("constraint uq_ai_sessions_one_active_per_project");
        when(sessions.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("conflict", cause));

        AiSessionException error = assertThrows(AiSessionException.class,
                () -> service.startOrReuse(projectId));

        assertEquals(AI_SESSION_CONCURRENT_START, error.getCode());
    }

    @Test
    void unrelatedIntegrityViolationIsNotHidden() {
        when(sessions.findFirstByProjectIdAndStatus(projectId, AiSessionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        DataIntegrityViolationException failure = new DataIntegrityViolationException("other constraint");
        when(sessions.saveAndFlush(any())).thenThrow(failure);

        assertSame(failure, assertThrows(DataIntegrityViolationException.class,
                () -> service.startOrReuse(projectId)));
    }

    private AiSession session(boolean active) {
        AiSession value = new AiSession(project, actor, NOW.minusSeconds(3600));
        ReflectionTestUtils.setField(value, "id", sessionId);
        if (!active) value.close(NOW.minusSeconds(60));
        return value;
    }

    private Project project(UUID id) {
        Project value = new Project("AI project", null);
        ReflectionTestUtils.setField(value, "id", id);
        return value;
    }

    private User user(UUID id, String name) {
        User value = new User(name.toLowerCase() + "@test.local", "hash", name);
        ReflectionTestUtils.setField(value, "id", id);
        return value;
    }
}
