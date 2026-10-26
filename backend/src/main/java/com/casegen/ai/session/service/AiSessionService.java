package com.casegen.ai.session.service;

import static com.casegen.ai.session.exception.AiSessionErrorCode.AI_SESSION_CONCURRENT_START;
import static com.casegen.ai.session.exception.AiSessionErrorCode.PROJECT_NOT_FOUND;
import static com.casegen.ai.session.exception.AiSessionErrorCode.SESSION_NOT_ACTIVE;
import static com.casegen.ai.session.exception.AiSessionErrorCode.SESSION_NOT_FOUND;

import com.casegen.ai.session.domain.AiSession;
import com.casegen.ai.session.domain.AiSessionParticipant;
import com.casegen.ai.session.domain.AiSessionStatus;
import com.casegen.ai.session.exception.AiSessionException;
import com.casegen.ai.session.repository.AiSessionParticipantRepository;
import com.casegen.ai.session.repository.AiSessionHistoryRow;
import com.casegen.ai.session.repository.AiSessionRepository;
import com.casegen.auth.domain.User;
import com.casegen.project.repository.ProjectRepository;
import com.casegen.project.service.ProjectAuthorizationService;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiSessionService {
    private static final String ACTIVE_SESSION_CONSTRAINT = "uq_ai_sessions_one_active_per_project";
    private static final int MAX_HISTORY_PAGE_SIZE = 100;

    private final AiSessionRepository sessions;
    private final AiSessionParticipantRepository participants;
    private final ProjectRepository projects;
    private final ProjectAuthorizationService authorization;
    private final Clock clock;

    public AiSessionService(AiSessionRepository sessions,
            AiSessionParticipantRepository participants, ProjectRepository projects,
            ProjectAuthorizationService authorization, Clock clock) {
        this.sessions = sessions;
        this.participants = participants;
        this.projects = projects;
        this.authorization = authorization;
        this.clock = clock;
    }

    @Transactional
    public StartOrReuseSessionResult startOrReuse(UUID projectId) {
        User actor = authorization.requireWrite(projectId);
        var project = projects.findByIdForUpdate(projectId)
                .orElseThrow(() -> new AiSessionException(PROJECT_NOT_FOUND, "Project does not exist"));
        Instant now = clock.instant();
        Optional<AiSession> active = sessions.findFirstByProjectIdAndStatus(projectId, AiSessionStatus.ACTIVE);
        if (active.isPresent()) {
            joinParticipant(active.get(), actor, now);
            return new StartOrReuseSessionResult(active.get(), false);
        }

        AiSession session = new AiSession(project, actor, now);
        try {
            sessions.saveAndFlush(session);
        } catch (DataIntegrityViolationException exception) {
            if (causedByActiveSessionConstraint(exception)) {
                throw new AiSessionException(AI_SESSION_CONCURRENT_START,
                        "Another session was started concurrently");
            }
            throw exception;
        }
        joinParticipant(session, actor, now);
        return new StartOrReuseSessionResult(session, true);
    }

    @Transactional(readOnly = true)
    public Optional<AiSession> getCurrent(UUID projectId) {
        authorization.requireActiveMemberOrSuperuser(projectId);
        return sessions.findFirstByProjectIdAndStatus(projectId, AiSessionStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public AiSession getSession(UUID projectId, UUID sessionId) {
        authorization.requireActiveMemberOrSuperuser(projectId);
        return requireSession(projectId, sessionId);
    }

    @Transactional
    public AiSession join(UUID projectId, UUID sessionId) {
        User actor = authorization.requireActiveMemberOrSuperuser(projectId);
        AiSession session = sessions.findByIdAndProjectIdForUpdate(sessionId, projectId)
                .orElseThrow(() -> notFound(sessionId));
        if (!session.isActive()) {
            throw new AiSessionException(SESSION_NOT_ACTIVE, "The session is not active");
        }
        joinParticipant(session, actor, clock.instant());
        return requireSession(projectId, sessionId);
    }

    @Transactional
    public AiSession end(UUID projectId, UUID sessionId) {
        authorization.requireWrite(projectId);
        AiSession session = sessions.findByIdAndProjectIdForUpdate(sessionId, projectId)
                .orElseThrow(() -> notFound(sessionId));
        session.close(clock.instant());
        return requireSession(projectId, sessionId);
    }

    @Transactional(readOnly = true)
    public Page<AiSessionHistoryRow> history(UUID projectId, Pageable pageable) {
        authorization.requireActiveMemberOrSuperuser(projectId);
        return sessions.findHistoryByProjectId(projectId, bounded(pageable));
    }

    private AiSession requireSession(UUID projectId, UUID sessionId) {
        return sessions.findByIdAndProjectId(sessionId, projectId)
                .orElseThrow(() -> notFound(sessionId));
    }

    private AiSessionException notFound(UUID sessionId) {
        return new AiSessionException(SESSION_NOT_FOUND,
                "Session " + sessionId + " does not exist in this project");
    }

    private void joinParticipant(AiSession session, User user, Instant now) {
        participants.findBySession_IdAndUser_Id(session.getId(), user.getId())
                .ifPresentOrElse(participant -> participant.touch(now), () -> {
                    AiSessionParticipant participant = new AiSessionParticipant(session, user, now);
                    participants.save(participant);
                    session.addParticipant(participant);
                });
    }

    private boolean causedByActiveSessionConstraint(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            String message = cause.getMessage();
            if (message != null && message.contains(ACTIVE_SESSION_CONSTRAINT)) return true;
            cause = cause.getCause();
        }
        return false;
    }

    private Pageable bounded(Pageable pageable) {
        if (pageable.isUnpaged()) return PageRequest.of(0, MAX_HISTORY_PAGE_SIZE);
        if (pageable.getPageSize() <= MAX_HISTORY_PAGE_SIZE) return pageable;
        return PageRequest.of(pageable.getPageNumber(), MAX_HISTORY_PAGE_SIZE, pageable.getSort());
    }
}
