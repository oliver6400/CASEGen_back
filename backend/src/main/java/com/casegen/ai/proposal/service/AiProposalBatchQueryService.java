package com.casegen.ai.proposal.service;

import com.casegen.ai.proposal.domain.*;
import com.casegen.ai.proposal.dto.*;
import com.casegen.ai.proposal.repository.*;
import com.casegen.project.service.ProjectAuthorizationService;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiProposalBatchQueryService {
    private final ProjectAuthorizationService authorization;
    private final AiProposalBatchRepository batches;
    private final AiProposalRepository proposals;
    private final AiProposalDependencyRepository dependencies;

    public AiProposalBatchQueryService(ProjectAuthorizationService authorization,
            AiProposalBatchRepository batches, AiProposalRepository proposals,
            AiProposalDependencyRepository dependencies) {
        this.authorization=authorization; this.batches=batches; this.proposals=proposals;
        this.dependencies=dependencies;
    }

    @Transactional(readOnly=true)
    public PagedAiProposalBatchResponse list(UUID projectId,int page,int size) {
        authorization.requireRead(projectId);
        var result=batches.findByProject_IdOrderByCreatedAtDescIdDesc(projectId,
                PageRequest.of(Math.max(0,page),Math.min(Math.max(size,1),50)));
        return new PagedAiProposalBatchResponse(result.getContent().stream().map(this::map).toList(),
                result.getNumber(),result.getSize(),result.getTotalElements(),result.getTotalPages(),
                result.isFirst(),result.isLast());
    }

    @Transactional(readOnly=true)
    public AiProposalBatchResponse detail(UUID projectId,UUID batchId) {
        authorization.requireRead(projectId);
        return map(batches.findByProject_IdAndId(projectId,batchId)
                .orElseThrow(()->new AiProposalException(AiProposalErrorCode.PROPOSAL_NOT_FOUND,
                        "AI proposal batch does not exist")));
    }

    @Transactional(readOnly=true)
    public AiProposalBatchResponse map(AiProposalBatch batch) {
        List<AiProposal> children=proposals.findByBatch_IdOrderByBatchOrderAsc(batch.getId());
        Map<UUID,List<AiProposal>> required=new HashMap<>();
        for(AiProposalDependency edge:dependencies.findByBatchId(batch.getId()))
            required.computeIfAbsent(edge.getProposal().getId(),ignored->new ArrayList<>()).add(edge.getDependsOn());
        List<AiProposalBatchChildResponse> mapped=children.stream().map(child->mapChild(child,
                required.getOrDefault(child.getId(),List.of()))).toList();
        return new AiProposalBatchResponse(batch.getId(),batch.getProject().getId(),batch.getSession().getId(),
                batch.getSourceMessage().getId(),actor(batch.getRequestedBy()),batch.getModel().getId(),
                batch.getModel().getName(),batch.getDiagram()==null?null:batch.getDiagram().getId(),
                batch.getDiagram()==null?null:batch.getDiagram().getName(),batch.getTitle(),batch.getSummary(),
                batch.getBaseModelRevision(),batch.getBaseDiagramRevision(),batch.getModelRevisionCursor(),
                batch.getModel().getRevision(),batch.getProvider(),batch.getSkill(),batch.getCreatedAt(),
                actor(batch.getAppliedBy()),batch.getAppliedAt(),derivedStatus(children),mapped);
    }

    private AiProposalBatchChildResponse mapChild(AiProposal child,List<AiProposal> required) {
        boolean blocked=child.getStatus()==AiProposalStatus.PENDING
                && required.stream().anyMatch(value->value.getStatus()!=AiProposalStatus.APPLIED);
        String reason=null;
        if(blocked) reason=required.stream().anyMatch(value->value.getStatus()==AiProposalStatus.REJECTED)
                ? "Bloqueada porque una dependencia fue rechazada"
                : "Requiere aplicar: "+required.stream().filter(value->value.getStatus()==AiProposalStatus.PENDING)
                    .map(AiProposal::getTitle).findFirst().orElse("otra propuesta");
        return new AiProposalBatchChildResponse(child.getId(),child.getBatch().getId(),child.getBatchOrder(),
                child.getClientRef(),child.getProposalType(),child.getTitle(),child.getRationale(),
                child.getStatus(),child.getPayload(),child.getPayloadSchemaVersion(),child.getBaseModelRevision(),
                child.getAppliedModelRevision(),child.getAppliedElementId(),actor(child.getRequestedBy()),
                actor(child.getDecidedBy()),child.getDecidedAt(),child.getDecisionNote(),child.getCreatedAt(),
                required.stream().map(value->new AiProposalBatchDependencyResponse(value.getId(),value.getTitle(),value.getStatus())).toList(),
                blocked,reason);
    }

    private AiProposalActorResponse actor(com.casegen.auth.domain.User user) {
        return user==null?null:new AiProposalActorResponse(user.getId(),user.getDisplayName());
    }
    private String derivedStatus(List<AiProposal> children) {
        long pending=children.stream().filter(value->value.getStatus()==AiProposalStatus.PENDING).count();
        long applied=children.stream().filter(value->value.getStatus()==AiProposalStatus.APPLIED).count();
        long rejected=children.size()-pending-applied;
        if(pending==children.size()) return "PENDING";
        if(pending>0) return "IN_PROGRESS";
        if(applied==children.size()) return "APPLIED";
        if(rejected==children.size()) return "REJECTED";
        return "COMPLETED";
    }
}
