package com.casegen.ai.proposal.service;

import com.casegen.ai.message.repository.AiMessageRepository;
import com.casegen.ai.proposal.domain.AiProposal;
import com.casegen.ai.proposal.domain.AiProposalStatus;
import com.casegen.ai.proposal.domain.AiProposalType;
import com.casegen.ai.proposal.repository.AiProposalRepository;
import com.casegen.ai.proposal.realtime.AiProposalEventPublisher;
import com.casegen.ai.session.repository.AiSessionRepository;
import com.casegen.auth.repository.UserRepository;
import com.casegen.modeling.repository.DiagramRepository;
import com.casegen.modeling.repository.UmlModelRepository;
import com.casegen.project.repository.ProjectRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiProposalPersistenceService {
    private final AiProposalRepository proposals;
    private final ProjectRepository projects;
    private final AiSessionRepository sessions;
    private final AiMessageRepository messages;
    private final UserRepository users;
    private final UmlModelRepository models;
    private final DiagramRepository diagrams;
    private final AiProposalEventPublisher events;

    public AiProposalPersistenceService(AiProposalRepository proposals, ProjectRepository projects,
            AiSessionRepository sessions, AiMessageRepository messages, UserRepository users,
            UmlModelRepository models, DiagramRepository diagrams,
            AiProposalEventPublisher events) {
        this.proposals = proposals;
        this.projects = projects;
        this.sessions = sessions;
        this.messages = messages;
        this.users = users;
        this.models = models;
        this.diagrams = diagrams;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public boolean projectExists(UUID projectId) {
        return projects.existsById(projectId);
    }

    @Transactional(readOnly = true)
    public Optional<AiProposal> findReplay(UUID projectId, UUID clientProposalId) {
        return proposals.findByProject_IdAndClientProposalId(projectId, clientProposalId);
    }

    @Transactional
    public AiProposal persist(UUID projectId, UUID sessionId, UUID sourceMessageId,
            UUID requestedById, UUID clientProposalId, GeneratedAiProposal generated) {
        var project = projects.findById(projectId)
                .orElseThrow(() -> error(AiProposalErrorCode.PROJECT_NOT_FOUND,
                        "Project does not exist"));
        var session = sessions.findByIdAndProjectId(sessionId, projectId)
                .orElseThrow(() -> error(AiProposalErrorCode.INVALID_SOURCE_CONTEXT,
                        "AI session does not belong to the project"));
        var source = messages.findDetailedById(sourceMessageId)
                .orElseThrow(() -> error(AiProposalErrorCode.INVALID_SOURCE_CONTEXT,
                        "Source message does not exist"));
        if (!source.getSession().getId().equals(sessionId)
                || !generated.sourceMessageId().equals(sourceMessageId)) {
            throw error(AiProposalErrorCode.INVALID_SOURCE_CONTEXT,
                    "Source message does not belong to the requested session");
        }
        var requester = users.findById(requestedById)
                .orElseThrow(() -> error(AiProposalErrorCode.INVALID_SOURCE_CONTEXT,
                        "Requesting user no longer exists"));
        var model = models.findById(generated.modelId())
                .orElseThrow(() -> error(AiProposalErrorCode.INVALID_SOURCE_CONTEXT,
                        "UML model no longer exists"));
        if (!model.getProject().getId().equals(projectId)
                || source.getModel() == null || !source.getModel().getId().equals(model.getId())) {
            throw error(AiProposalErrorCode.INVALID_SOURCE_CONTEXT,
                    "UML model does not belong to the proposal project and source");
        }
        if (model.getRevision() != generated.baseModelRevision()) {
            throw error(AiProposalErrorCode.CONTEXT_REVISION_CHANGED,
                    "UML model changed while the proposal was being generated");
        }
        var diagram = generated.diagramId() == null ? null : diagrams.findById(generated.diagramId())
                .orElseThrow(() -> error(AiProposalErrorCode.INVALID_SOURCE_CONTEXT,
                        "Diagram no longer exists"));
        if (diagram != null && (!diagram.getModel().getId().equals(model.getId())
                || source.getDiagram() == null || !source.getDiagram().getId().equals(diagram.getId()))) {
            throw error(AiProposalErrorCode.INVALID_SOURCE_CONTEXT,
                    "Diagram does not belong to the proposal model and source");
        }
        if (diagram != null && !java.util.Objects.equals(
                diagram.getRevision(), generated.baseDiagramRevision())) {
            throw error(AiProposalErrorCode.CONTEXT_REVISION_CHANGED,
                    "Diagram changed while the proposal was being generated");
        }
        AiProposal persisted = proposals.saveAndFlush(AiProposal.pending(project, session, source, requester,
                model, diagram, generated.proposalType(), generated.title(), generated.rationale(),
                generated.canonicalPayload(), (short) 1, generated.baseModelRevision(),
                generated.baseDiagramRevision(), generated.provider(), clientProposalId));
        events.created(projectId, persisted.getId());
        return persisted;
    }

    @Transactional(readOnly = true)
    public Page<AiProposal> list(UUID projectId, AiProposalStatus status, UUID sessionId,
            UUID modelId, UUID diagramId, AiProposalType proposalType, int page, int size) {
        return proposals.findFiltered(projectId, status, sessionId, modelId, diagramId,
                proposalType, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public AiProposal detail(UUID projectId, UUID proposalId) {
        return proposals.findByIdAndProject_Id(proposalId, projectId)
                .orElseThrow(() -> error(AiProposalErrorCode.PROPOSAL_NOT_FOUND,
                        "AI proposal does not exist"));
    }

    private AiProposalException error(AiProposalErrorCode code, String message) {
        return new AiProposalException(code, message);
    }
}
