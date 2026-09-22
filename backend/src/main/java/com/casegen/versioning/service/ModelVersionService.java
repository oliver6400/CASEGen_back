package com.casegen.versioning.service;

import com.casegen.modeling.service.ModelSnapshotReader;
import com.casegen.validation.service.ValidationService;
import com.casegen.versioning.dto.ModelVersionResponse;
import com.casegen.versioning.repository.ModelVersionRepository;
import com.casegen.shared.exception.WorkflowConflictException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelVersionService {
    private final ModelSnapshotReader snapshots;
    private final ValidationService validation;
    private final ModelVersionRepository versions;
    public ModelVersionService(ModelSnapshotReader snapshots, ValidationService validation, ModelVersionRepository versions) {
        this.snapshots = snapshots; this.validation = validation; this.versions = versions;
    }
    @Transactional
    public ModelVersionResponse create(UUID modelId) {
        var model=snapshots.requireModel(modelId, true);
        // The same aggregate lock serializes semantic edits and competing version
        // requests. Keep existing historical duplicates; reject new ones by revision.
        versions.list(modelId).stream().filter(v->v.semanticSnapshot().path("model").path("revision").asLong(-1)==model.getRevision())
                .findFirst().ifPresent(v->{ throw new WorkflowConflictException("MODEL_REVISION_ALREADY_VERSIONED",
                    "La revisión r"+model.getRevision()+" ya está versionada como v"+v.versionNumber()+"."); });
        var validated = validation.requireValidCurrent(modelId);
        UUID id = UUID.randomUUID();
        versions.append(id, modelId, validated.id(), snapshots.actor(), snapshots.semantic(modelId), snapshots.diagrams(modelId));
        return versions.list(modelId).stream().filter(v -> v.id().equals(id)).findFirst().orElseThrow();
    }
    @Transactional(readOnly = true)
    public List<ModelVersionResponse> list(UUID modelId) { snapshots.requireModel(modelId, false); return versions.list(modelId); }
}
