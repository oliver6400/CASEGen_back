package com.casegen.ai.proposal.review;

import com.casegen.ai.proposal.domain.AiProposalBatch;
import com.casegen.ai.proposal.repository.AiProposalBatchRepository;
import com.casegen.ai.proposal.service.AiProposalErrorCode;
import com.casegen.ai.proposal.service.AiProposalException;
import com.casegen.auth.domain.User;
import com.casegen.project.service.ProjectAuthorizationService;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** Orchestrates generation outside the short database transaction. */
@Service
public class AiProposalBatchCreationService {
    private final ProjectAuthorizationService authorization;
    private final AiProposalBatchRepository batches;
    private final AiProposalBatchGenerator generator;
    private final AiUmlReviewSnapshotBuilder snapshots;
    private final AiReviewSemanticValidator validator;
    private final AiProposalBatchPersistenceService persistence;

    public AiProposalBatchCreationService(ProjectAuthorizationService authorization,
            AiProposalBatchRepository batches, AiProposalBatchGenerator generator,
            AiUmlReviewSnapshotBuilder snapshots, AiReviewSemanticValidator validator,
            AiProposalBatchPersistenceService persistence) {
        this.authorization = authorization;
        this.batches = batches;
        this.generator = generator;
        this.snapshots = snapshots;
        this.validator = validator;
        this.persistence = persistence;
    }

    public CreatedAiProposalBatch create(CreateAiProposalBatchCommand command) {
        requireCommand(command);
        User actor = authorization.requireActiveMemberOrSuperuser(command.projectId());
        if (!actor.getId().equals(command.actorUserId())) {
            throw new AiProposalException(AiProposalErrorCode.INVALID_REQUEST,
                    "actorUserId must identify the authenticated user");
        }
        AiProposalBatch replay = batches
                .findByProject_IdAndClientBatchId(command.projectId(), command.clientBatchId())
                .orElse(null);
        if (replay != null) return replay(command, replay);

        GeneratedAiReview generated = generator.generate(new GenerateAiReviewCommand(
                command.projectId(), command.sessionId(), command.sourceMessageId()));
        AiUmlReviewSnapshot current = snapshots.build(command.projectId(), generated.modelId(),
                generated.diagramId());
        ValidatedAiReview validated = validator.validate(generated, current);
        try {
            return persistence.persist(command, validated);
        } catch (DataIntegrityViolationException concurrentCreate) {
            AiProposalBatch winner = batches
                    .findByProject_IdAndClientBatchId(command.projectId(), command.clientBatchId())
                    .orElseThrow(() -> concurrentCreate);
            return replay(command, winner);
        }
    }

    private CreatedAiProposalBatch replay(CreateAiProposalBatchCommand command, AiProposalBatch batch) {
        if (!batch.getSession().getId().equals(command.sessionId())
                || !batch.getSourceMessage().getId().equals(command.sourceMessageId())
                || !batch.getRequestedBy().getId().equals(command.actorUserId())) {
            throw new AiProposalException(AiProposalErrorCode.CLIENT_BATCH_ID_REUSED,
                    "clientBatchId is already associated with another request");
        }
        return new CreatedAiProposalBatch(batch, true);
    }

    private void requireCommand(CreateAiProposalBatchCommand command) {
        if (command == null || command.projectId() == null || command.sessionId() == null
                || command.sourceMessageId() == null || command.actorUserId() == null
                || command.clientBatchId() == null) {
            throw new AiProposalException(AiProposalErrorCode.INVALID_REQUEST,
                    "projectId, sessionId, sourceMessageId, actorUserId and clientBatchId are required");
        }
    }
}
