package com.casegen.generation.backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.casegen.auth.domain.User;
import com.casegen.generation.backend.dto.GenerationDtos.Response;
import com.casegen.generation.backend.repository.GenerationRunRepository;
import com.casegen.generation.backend.service.*;
import com.casegen.generation.logical.dto.LogicalModelDtos;
import com.casegen.generation.logical.service.LogicalModelService;
import com.casegen.project.service.ProjectAuthorizationService;
import java.time.Duration;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.*;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

class GenerationServiceTest {
    @Test
    void requestPersistsQueuedRunAndReturnsBeforeWorkerStarts() {
        GenerationRunRepository runs=mock(GenerationRunRepository.class);
        LogicalModelService logical=mock(LogicalModelService.class);
        ProjectAuthorizationService authorization=mock(ProjectAuthorizationService.class);
        SpringBackendRenderer renderer=mock(SpringBackendRenderer.class);
        IsolatedPostgresVerifier postgres=mock(IsolatedPostgresVerifier.class);
        CapturingExecutor executor=new CapturingExecutor();
        UUID logicalId=UUID.randomUUID(),projectId=UUID.randomUUID(),actorId=UUID.randomUUID();
        var source=source(logicalId,projectId);
        User actor=mock(User.class);LogicalModelDtos.Response input=mock(LogicalModelDtos.Response.class);
        when(runs.source(logicalId)).thenReturn(Optional.of(source));when(authorization.requireGenerate(projectId)).thenReturn(actor);
        when(actor.getId()).thenReturn(actorId);when(logical.get(logicalId)).thenReturn(input);
        var service=new GenerationService(runs,logical,authorization,renderer,postgres,executor,"target/generation-unit",Duration.ofSeconds(1));

        var accepted=service.generate(logicalId);

        assertEquals("QUEUED",accepted.status());assertNotNull(accepted.generationId());assertNotNull(executor.task);
        verify(runs).create(accepted.generationId(),source,actorId,GenerationService.VERSION);
        verifyNoInteractions(renderer,postgres);
    }

    @Test
    void historyChecksReadPermissionAndKeepsRepositoryOrder() {
        GenerationRunRepository runs=mock(GenerationRunRepository.class);LogicalModelService logical=mock(LogicalModelService.class);
        ProjectAuthorizationService authorization=mock(ProjectAuthorizationService.class);UUID logicalId=UUID.randomUUID(),projectId=UUID.randomUUID();
        var source=source(logicalId,projectId);
        List<Response> expected=List.of(mock(Response.class),mock(Response.class));when(runs.source(logicalId)).thenReturn(Optional.of(source));when(runs.list(logicalId)).thenReturn(expected);
        var service=new GenerationService(runs,logical,authorization,mock(SpringBackendRenderer.class),mock(IsolatedPostgresVerifier.class),Runnable::run,"target/generation-unit",Duration.ofSeconds(1));
        assertSame(expected,service.history(logicalId));verify(authorization).requireRead(projectId);
    }

    @Test
    void downloadRequiresSuccessfulMatchingArtifactAndGeneratePermission(@TempDir Path runtime)throws Exception {
        GenerationRunRepository runs=mock(GenerationRunRepository.class);ProjectAuthorizationService authorization=mock(ProjectAuthorizationService.class);
        UUID runId=UUID.randomUUID(),artifactId=UUID.randomUUID(),projectId=UUID.randomUUID();Path file=runtime.resolve(runId+"/artifacts/backend.zip");Files.createDirectories(file.getParent());Files.writeString(file,"zip-content");
        String checksum=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        when(runs.storedArtifact(runId,artifactId)).thenReturn(Optional.of(new GenerationRunRepository.StoredArtifact(runId,artifactId,projectId,"SUCCESS",runId+"/artifacts/backend.zip","backend.zip",checksum,Files.size(file))));
        var service=new GenerationService(runs,mock(LogicalModelService.class),authorization,mock(SpringBackendRenderer.class),mock(IsolatedPostgresVerifier.class),Runnable::run,runtime.toString(),Duration.ofSeconds(1));
        var download=service.download(runId,artifactId);assertEquals("backend.zip",download.fileName());assertArrayEquals(Files.readAllBytes(file),download.resource().getInputStream().readAllBytes());verify(authorization).requireGenerate(projectId);
        when(runs.storedArtifact(runId,artifactId)).thenReturn(Optional.of(new GenerationRunRepository.StoredArtifact(runId,artifactId,projectId,"FAILED",runId+"/artifacts/backend.zip","backend.zip",checksum,Files.size(file))));
        assertThrows(ResponseStatusException.class,()->service.download(runId,artifactId));
        doThrow(new AccessDeniedException("denied")).when(authorization).requireGenerate(projectId);assertThrows(AccessDeniedException.class,()->service.download(runId,artifactId));
    }

    private GenerationRunRepository.Source source(UUID logicalId,UUID projectId) {
        return new GenerationRunRepository.Source(logicalId,UUID.randomUUID(),UUID.randomUUID(),projectId,
                UUID.randomUUID(),"Model",3,"VALID");
    }
    private static final class CapturingExecutor implements Executor { private Runnable task;public void execute(Runnable command){task=command;} }
}
