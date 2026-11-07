package com.casegen.ai.proposal.service;

import com.casegen.ai.proposal.domain.*;
import com.casegen.ai.proposal.repository.*;
import com.casegen.ai.proposal.realtime.AiProposalEventPublisher;
import com.casegen.auth.domain.User;
import com.casegen.modeling.repository.UmlModelRepository;
import com.casegen.project.service.ProjectAuthorizationService;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiProposalBatchApplyAllService {
    private final ProjectAuthorizationService authorization;
    private final AiProposalBatchRepository batches;
    private final AiProposalRepository proposals;
    private final AiProposalDependencyRepository dependencyRepository;
    private final UmlModelRepository models;
    private final AiProposalBatchChildApplicator applicator;
    private final AiProposalEventPublisher events;
    private final Clock clock;

    public AiProposalBatchApplyAllService(ProjectAuthorizationService authorization,
            AiProposalBatchRepository batches, AiProposalRepository proposals,
            AiProposalDependencyRepository dependencyRepository, UmlModelRepository models,
            AiProposalBatchChildApplicator applicator, AiProposalEventPublisher events,
            Clock clock) {
        this.authorization = authorization;
        this.batches = batches;
        this.proposals = proposals;
        this.dependencyRepository = dependencyRepository;
        this.models = models;
        this.applicator = applicator;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public AiProposalBatchApplyAllResult apply(AiProposalBatchApplyAllCommand command) {
        requireCommand(command);
        User actor = authorization.requireWrite(command.projectId());
        if (!actor.getId().equals(command.actorUserId())) {
            throw error(AiProposalErrorCode.INVALID_REQUEST,
                    "Decision actor must be the authenticated user");
        }

        // Global decision lock order: batch -> children (stable order) -> model.
        AiProposalBatch batch = batches.findForUpdate(command.projectId(), command.batchId())
                .orElseThrow(() -> error(AiProposalErrorCode.PROPOSAL_NOT_FOUND,
                        "AI proposal batch does not exist"));
        List<AiProposal> children = proposals.findBatchChildrenForUpdate(batch.getId());
        List<AiProposal> pending = children.stream()
                .filter(value -> value.getStatus() == AiProposalStatus.PENDING).toList();
        if (pending.isEmpty()) {
            throw error(AiProposalErrorCode.BATCH_ALREADY_DECIDED,
                    "AI proposal batch has no pending proposals");
        }
        var model = models.findByIdForUpdate(batch.getModel().getId())
                .orElseThrow(() -> error(AiProposalErrorCode.PROPOSAL_NO_LONGER_VALID,
                        "Batch UML model no longer exists"));
        if (model.getRevision() != batch.getModelRevisionCursor()) {
            throw error(AiProposalErrorCode.BATCH_STALE,
                    "UML model changed outside this proposal batch");
        }

        List<AiProposalDependency> allDependencies = dependencyRepository.findByBatchId(batch.getId());
        Map<UUID, List<AiProposalDependency>> byProposal = new HashMap<>();
        for (AiProposalDependency dependency : allDependencies) {
            byProposal.computeIfAbsent(dependency.getProposal().getId(), ignored -> new ArrayList<>())
                    .add(dependency);
        }
        List<AiProposal> ordered = topologicalOrder(pending, byProposal);
        Instant decidedAt = Instant.now(clock);
        for (AiProposal child : ordered) {
            applicator.apply(batch, child,
                    byProposal.getOrDefault(child.getId(), List.of()), actor, decidedAt,
                    command.note());
            proposals.save(child);
        }
        batch.recordApplyAll(actor, decidedAt);
        proposals.flush();
        batches.saveAndFlush(batch);
        events.batchApplied(command.projectId(), batch.getId(), ordered.size(),
                batch.getModelRevisionCursor(), decidedAt);
        return new AiProposalBatchApplyAllResult(batch.getId(), ordered.size(),
                batch.getModelRevisionCursor(), decidedAt);
    }

    private List<AiProposal> topologicalOrder(List<AiProposal> pending,
            Map<UUID, List<AiProposalDependency>> dependencies) {
        Map<UUID, AiProposal> pendingById = new HashMap<>();
        pending.forEach(value -> pendingById.put(value.getId(), value));
        Map<UUID, Integer> degree = new HashMap<>();
        Map<UUID, List<UUID>> outgoing = new HashMap<>();
        pending.forEach(value -> degree.put(value.getId(), 0));
        for (AiProposal proposal : pending) {
            for (AiProposalDependency dependency : dependencies
                    .getOrDefault(proposal.getId(), List.of())) {
                AiProposal required = dependency.getDependsOn();
                if (required.getStatus() == AiProposalStatus.REJECTED) {
                    throw error(AiProposalErrorCode.BATCH_HAS_UNSATISFIED_DEPENDENCY,
                            "A pending proposal depends on a rejected proposal");
                }
                if (required.getStatus() == AiProposalStatus.PENDING) {
                    if (!pendingById.containsKey(required.getId())) {
                        throw error(AiProposalErrorCode.BATCH_HAS_UNSATISFIED_DEPENDENCY,
                                "A proposal dependency is not part of this batch execution");
                    }
                    degree.merge(proposal.getId(), 1, Integer::sum);
                    outgoing.computeIfAbsent(required.getId(), ignored -> new ArrayList<>())
                            .add(proposal.getId());
                }
            }
        }
        Comparator<AiProposal> stable = Comparator
                .comparing(AiProposal::getBatchOrder).thenComparing(AiProposal::getId);
        PriorityQueue<AiProposal> ready = new PriorityQueue<>(stable);
        pending.stream().filter(value -> degree.get(value.getId()) == 0).forEach(ready::add);
        List<AiProposal> ordered = new ArrayList<>();
        while (!ready.isEmpty()) {
            AiProposal current = ready.remove();
            ordered.add(current);
            for (UUID nextId : outgoing.getOrDefault(current.getId(), List.of())) {
                if (degree.merge(nextId, -1, Integer::sum) == 0) ready.add(pendingById.get(nextId));
            }
        }
        if (ordered.size() != pending.size()) {
            throw error(AiProposalErrorCode.BATCH_HAS_UNSATISFIED_DEPENDENCY,
                    "Proposal dependency graph is not executable");
        }
        return ordered;
    }

    private void requireCommand(AiProposalBatchApplyAllCommand command) {
        if (command == null || command.projectId() == null || command.batchId() == null
                || command.actorUserId() == null) {
            throw error(AiProposalErrorCode.INVALID_REQUEST,
                    "Project, batch and actor are required");
        }
        if (command.note() != null && command.note().length() > 1000) {
            throw error(AiProposalErrorCode.INVALID_REQUEST,
                    "Decision note exceeds maximum length 1000");
        }
    }

    private AiProposalException error(AiProposalErrorCode code, String message) {
        return new AiProposalException(code, message);
    }
}
