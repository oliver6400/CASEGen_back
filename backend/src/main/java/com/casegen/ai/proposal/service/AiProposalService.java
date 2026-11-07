package com.casegen.ai.proposal.service;

import com.casegen.ai.proposal.domain.AiProposal;
import com.casegen.ai.proposal.domain.AiProposalStatus;
import com.casegen.ai.proposal.domain.AiProposalType;
import com.casegen.ai.proposal.dto.CreateAiProposalRequest;
import com.casegen.auth.domain.User;
import com.casegen.project.service.ProjectAuthorizationService;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

@Service
public class AiProposalService {
    private static final int MAX_PAGE_SIZE = 100;
    private final ProjectAuthorizationService authorization;
    private final AiProposalGenerator generator;
    private final AiProposalPersistenceService persistence;
    private final ConcurrentHashMap<ProposalKey, InFlight> inFlight = new ConcurrentHashMap<>();

    public AiProposalService(ProjectAuthorizationService authorization,
            AiProposalGenerator generator, AiProposalPersistenceService persistence) {
        this.authorization = authorization;
        this.generator = generator;
        this.persistence = persistence;
    }

    public CreateAiProposalResult create(UUID projectId, UUID sessionId, UUID sourceMessageId,
            CreateAiProposalRequest request) {
        User actor = authorization.requireActiveMemberOrSuperuser(projectId);
        if (!persistence.projectExists(projectId)) {
            throw error(AiProposalErrorCode.PROJECT_NOT_FOUND, "Project does not exist");
        }
        if (request == null || request.clientProposalId() == null) {
            throw error(AiProposalErrorCode.INVALID_REQUEST, "clientProposalId is required");
        }
        LogicalAttempt attempt = new LogicalAttempt(sessionId, sourceMessageId, actor.getId());
        var existing = persistence.findReplay(projectId, request.clientProposalId());
        if (existing.isPresent()) {
            requireSameAttempt(existing.get(), attempt);
            return new CreateAiProposalResult(existing.get(), true);
        }

        ProposalKey key = new ProposalKey(projectId, request.clientProposalId());
        InFlight mine = new InFlight(attempt, new CompletableFuture<>());
        InFlight current = inFlight.putIfAbsent(key, mine);
        if (current != null) {
            if (!current.attempt().equals(attempt)) throw reused();
            return new CreateAiProposalResult(await(current.result()).proposal(), true);
        }
        try {
            CreateAiProposalResult result = generateAndPersist(projectId, request.clientProposalId(),
                    attempt);
            mine.result().complete(result);
            return result;
        } catch (Throwable failure) {
            mine.result().completeExceptionally(failure);
            if (failure instanceof RuntimeException runtime) throw runtime;
            if (failure instanceof Error error) throw error;
            throw new IllegalStateException(failure);
        } finally {
            inFlight.remove(key, mine);
        }
    }

    public Page<AiProposal> list(UUID projectId, AiProposalStatus status, UUID sessionId,
            UUID modelId, UUID diagramId, AiProposalType proposalType, int page, int size) {
        authorization.requireActiveMemberOrSuperuser(projectId);
        validatePage(page, size);
        return persistence.list(projectId, status, sessionId, modelId, diagramId, proposalType,
                page, size);
    }

    public AiProposal detail(UUID projectId, UUID proposalId) {
        authorization.requireActiveMemberOrSuperuser(projectId);
        return persistence.detail(projectId, proposalId);
    }

    private CreateAiProposalResult generateAndPersist(UUID projectId, UUID clientProposalId,
            LogicalAttempt attempt) {
        GeneratedAiProposal generated = generator.generate(new GenerateAiProposalCommand(projectId,
                attempt.sessionId(), attempt.sourceMessageId()));
        try {
            AiProposal proposal = persistence.persist(projectId, attempt.sessionId(),
                    attempt.sourceMessageId(), attempt.requestedById(), clientProposalId, generated);
            return new CreateAiProposalResult(proposal, false);
        } catch (DataIntegrityViolationException collision) {
            AiProposal existing = persistence.findReplay(projectId, clientProposalId)
                    .orElseThrow(this::reused);
            requireSameAttempt(existing, attempt);
            return new CreateAiProposalResult(existing, true);
        }
    }

    private void requireSameAttempt(AiProposal proposal, LogicalAttempt attempt) {
        boolean same = proposal.getSession().getId().equals(attempt.sessionId())
                && proposal.getSourceMessage().getId().equals(attempt.sourceMessageId())
                && proposal.getRequestedBy().getId().equals(attempt.requestedById());
        if (!same) throw reused();
    }

    private CreateAiProposalResult await(CompletableFuture<CreateAiProposalResult> result) {
        try {
            return result.join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException runtime) throw runtime;
            if (exception.getCause() instanceof Error error) throw error;
            throw exception;
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw error(AiProposalErrorCode.INVALID_REQUEST,
                    "page must be at least 0 and size must be between 1 and 100");
        }
    }

    private AiProposalException reused() {
        return error(AiProposalErrorCode.CLIENT_PROPOSAL_ID_REUSED,
                "clientProposalId was already used for a different proposal request");
    }

    private AiProposalException error(AiProposalErrorCode code, String message) {
        return new AiProposalException(code, message);
    }

    private record ProposalKey(UUID projectId, UUID clientProposalId) {
    }

    private record LogicalAttempt(UUID sessionId, UUID sourceMessageId, UUID requestedById) {
    }

    private record InFlight(LogicalAttempt attempt,
                            CompletableFuture<CreateAiProposalResult> result) {
    }
}
