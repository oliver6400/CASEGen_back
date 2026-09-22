package com.casegen.validation.service;

import com.casegen.modeling.service.ModelSnapshotReader;
import com.casegen.validation.dto.ValidationResponse;
import com.casegen.validation.repository.ValidationRepository;
import com.casegen.shared.exception.WorkflowConflictException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ValidationService {
    private final ModelSnapshotReader snapshots;
    private final ValidationRepository runs;
    private final SemanticValidationEngine engine;
    public ValidationService(ModelSnapshotReader snapshots, ValidationRepository runs, SemanticValidationEngine engine) {
        this.snapshots = snapshots; this.runs = runs; this.engine = engine;
    }
    @Transactional
    public ValidationResponse validate(UUID modelId) {
        // Older runs/versions never gate validation. Serialize with semantic edits
        // and capture a brand-new run for the currently locked semantic revision.
        var model = snapshots.requireModel(modelId, true);
        UUID id = UUID.randomUUID();
        String semantic = snapshots.semantic(modelId);
        runs.start(id, modelId, model.getRevision(), ModelSnapshotReader.checksum(snapshots.generation(modelId)), snapshots.actor());
        runs.finish(id, engine.validate(snapshots.parse(semantic)));
        return current(runs.find(id).orElseThrow());
    }
    @Transactional(readOnly = true)
    public ValidationResponse get(UUID id) {
        var run = runs.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Validation not found"));
        return current(run);
    }
    @Transactional(readOnly = true)
    public Optional<ValidationResponse> latest(UUID modelId) {
        snapshots.requireModel(modelId, false);
        return runs.latest(modelId).map(this::current);
    }
    public ValidationResponse requireValidCurrent(UUID modelId) {
        var run = latest(modelId).orElseThrow(() -> new WorkflowConflictException("VALIDATION_REQUIRED","Valida la revisión semántica actual antes de crear una versión."));
        if (!run.current()) throw new WorkflowConflictException("VALIDATION_STALE","La validación de r"+run.modelRevision()+" está obsoleta. Valida la revisión semántica actual.");
        if (!run.status().equals("VALID")) throw new WorkflowConflictException("VALIDATION_INVALID","La última validación no es VALID. Corrige los errores y vuelve a validar.");
        return run;
    }
    private ValidationResponse current(ValidationResponse run) {
        var model = snapshots.requireModel(run.modelId(), false);
        boolean current = model.getRevision() == run.modelRevision() && runs.generationChecksum(run.id()).equals(ModelSnapshotReader.checksum(snapshots.generation(run.modelId())));
        return new ValidationResponse(run.id(), run.modelId(), run.modelRevision(), run.status(), current, run.startedAt(), run.finishedAt(), run.issues());
    }
}
