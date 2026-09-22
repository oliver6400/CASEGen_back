package com.casegen.collaboration;

import com.casegen.modeling.repository.DiagramRepository;
import com.casegen.modeling.repository.UmlModelRepository;
import com.casegen.project.service.ProjectAuthorizationService;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RealtimeSubscriptionAuthorizationService {
    private final UmlModelRepository models;
    private final DiagramRepository diagrams;
    private final ProjectAuthorizationService authorization;

    public RealtimeSubscriptionAuthorizationService(UmlModelRepository models,
            DiagramRepository diagrams, ProjectAuthorizationService authorization) {
        this.models = models;
        this.diagrams = diagrams;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public void requireModelRead(UUID modelId, UUID userId) {
        UUID projectId = models.findById(modelId)
                .map(model -> model.getProject().getId())
                .orElseThrow(() -> new AccessDeniedException("UML model is unavailable"));
        requireProjectRead(projectId, userId);
    }

    @Transactional(readOnly = true)
    public void requireDiagramRead(UUID diagramId, UUID userId) {
        UUID projectId = diagrams.findById(diagramId)
                .map(diagram -> diagram.getModel().getProject().getId())
                .orElseThrow(() -> new AccessDeniedException("Diagram is unavailable"));
        requireProjectRead(projectId, userId);
    }

    @Transactional(readOnly = true)
    public void requireDiagramWrite(UUID diagramId, UUID userId) {
        UUID projectId = diagrams.findById(diagramId)
                .map(diagram -> diagram.getModel().getProject().getId())
                .orElseThrow(() -> new AccessDeniedException("Diagram is unavailable"));
        if (!authorization.canWriteAs(projectId, userId)) {
            throw new AccessDeniedException("The user cannot edit this project");
        }
    }

    private void requireProjectRead(UUID projectId, UUID userId) {
        if (!authorization.canReadAs(projectId, userId)) {
            throw new AccessDeniedException("The user cannot subscribe to this project");
        }
    }
}
