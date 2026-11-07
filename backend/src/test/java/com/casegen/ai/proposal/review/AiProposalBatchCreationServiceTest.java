package com.casegen.ai.proposal.review;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.casegen.ai.message.domain.AiMessage;
import com.casegen.ai.proposal.domain.AiProposalBatch;
import com.casegen.ai.proposal.repository.AiProposalBatchRepository;
import com.casegen.ai.proposal.service.*;
import com.casegen.ai.session.domain.AiSession;
import com.casegen.auth.domain.User;
import com.casegen.project.service.ProjectAuthorizationService;
import java.util.*;
import org.junit.jupiter.api.Test;

class AiProposalBatchCreationServiceTest {
    @Test
    void compatibleReplayDoesNotCallProviderOrValidator() {
        UUID projectId=UUID.randomUUID(),sessionId=UUID.randomUUID(),sourceId=UUID.randomUUID();
        UUID actorId=UUID.randomUUID(),clientId=UUID.randomUUID();
        User actor=mock(User.class);when(actor.getId()).thenReturn(actorId);
        AiSession session=mock(AiSession.class);when(session.getId()).thenReturn(sessionId);
        AiMessage source=mock(AiMessage.class);when(source.getId()).thenReturn(sourceId);
        AiProposalBatch batch=mock(AiProposalBatch.class);
        when(batch.getSession()).thenReturn(session);when(batch.getSourceMessage()).thenReturn(source);
        when(batch.getRequestedBy()).thenReturn(actor);
        ProjectAuthorizationService auth=mock(ProjectAuthorizationService.class);
        when(auth.requireActiveMemberOrSuperuser(projectId)).thenReturn(actor);
        AiProposalBatchRepository batches=mock(AiProposalBatchRepository.class);
        when(batches.findByProject_IdAndClientBatchId(projectId,clientId)).thenReturn(Optional.of(batch));
        AiProposalBatchGenerator generator=mock(AiProposalBatchGenerator.class);
        AiReviewSemanticValidator validator=mock(AiReviewSemanticValidator.class);
        var service=new AiProposalBatchCreationService(auth,batches,generator,
                mock(AiUmlReviewSnapshotBuilder.class),validator,
                mock(AiProposalBatchPersistenceService.class));

        CreatedAiProposalBatch result=service.create(new CreateAiProposalBatchCommand(
                projectId,sessionId,sourceId,actorId,clientId));

        assertTrue(result.replayed());assertSame(batch,result.batch());
        verifyNoInteractions(generator,validator);
    }

    @Test
    void incompatibleReplayIsRejectedBeforeProvider() {
        UUID projectId=UUID.randomUUID(),actorId=UUID.randomUUID(),clientId=UUID.randomUUID();
        User actor=mock(User.class);when(actor.getId()).thenReturn(actorId);
        AiProposalBatch batch=mock(AiProposalBatch.class);AiSession session=mock(AiSession.class);
        when(session.getId()).thenReturn(UUID.randomUUID());when(batch.getSession()).thenReturn(session);
        ProjectAuthorizationService auth=mock(ProjectAuthorizationService.class);
        when(auth.requireActiveMemberOrSuperuser(projectId)).thenReturn(actor);
        AiProposalBatchRepository batches=mock(AiProposalBatchRepository.class);
        when(batches.findByProject_IdAndClientBatchId(projectId,clientId)).thenReturn(Optional.of(batch));
        AiProposalBatchGenerator generator=mock(AiProposalBatchGenerator.class);
        var service=new AiProposalBatchCreationService(auth,batches,generator,
                mock(AiUmlReviewSnapshotBuilder.class),mock(AiReviewSemanticValidator.class),
                mock(AiProposalBatchPersistenceService.class));

        AiProposalException error=assertThrows(AiProposalException.class,()->service.create(
                new CreateAiProposalBatchCommand(projectId,UUID.randomUUID(),UUID.randomUUID(),actorId,clientId)));
        assertEquals(AiProposalErrorCode.CLIENT_BATCH_ID_REUSED,error.getCode());
        verifyNoInteractions(generator);
    }
}
