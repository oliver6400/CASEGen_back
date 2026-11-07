package com.casegen.ai.proposal.controller;

import com.casegen.ai.proposal.domain.AiProposalStatus;
import com.casegen.ai.proposal.domain.AiProposalType;
import com.casegen.ai.proposal.dto.AiProposalMapper;
import com.casegen.ai.proposal.dto.AiProposalDecisionRequest;
import com.casegen.ai.proposal.dto.AiProposalResponse;
import com.casegen.ai.proposal.dto.CreateAiProposalRequest;
import com.casegen.ai.proposal.dto.CreateAiProposalResponse;
import com.casegen.ai.proposal.dto.PagedAiProposalResponse;
import com.casegen.ai.proposal.service.AiProposalService;
import com.casegen.ai.proposal.service.AiProposalDecisionCommand;
import com.casegen.ai.proposal.service.AiProposalDecisionService;
import com.casegen.ai.proposal.service.AiProposalBatchApplyAllService;
import com.casegen.ai.proposal.service.AiProposalBatchApplyAllCommand;
import com.casegen.ai.proposal.dto.AiProposalBatchApplyAllResponse;
import com.casegen.ai.proposal.dto.*;
import com.casegen.ai.proposal.review.*;
import com.casegen.ai.proposal.service.AiProposalBatchQueryService;
import com.casegen.auth.service.CurrentUserService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;

@RestController
@RequestMapping("/api/projects/{projectId}/ai")
public class AiProposalController {
    private final AiProposalService service;
    private final AiProposalDecisionService decisions;
    private final AiProposalMapper mapper;
    private final CurrentUserService currentUsers;
    private final AiProposalBatchApplyAllService applyAll;
    private final AiProposalBatchCreationService batchCreation;
    private final AiProposalBatchQueryService batchQuery;

    @Autowired
    public AiProposalController(AiProposalService service, AiProposalDecisionService decisions,
            AiProposalMapper mapper, CurrentUserService currentUsers,
            AiProposalBatchApplyAllService applyAll,
            AiProposalBatchCreationService batchCreation,
            AiProposalBatchQueryService batchQuery) {
        this.service = service;
        this.decisions = decisions;
        this.mapper = mapper;
        this.currentUsers = currentUsers;
        this.applyAll = applyAll;
        this.batchCreation=batchCreation; this.batchQuery=batchQuery;
    }

    AiProposalController(AiProposalService service, AiProposalDecisionService decisions,
            AiProposalMapper mapper, CurrentUserService currentUsers) {
        this(service, decisions, mapper, currentUsers, null, null, null);
    }

    @PostMapping("/sessions/{sessionId}/messages/{messageId}/proposal-batches")
    public ResponseEntity<CreateAiProposalBatchResponse> createBatch(@PathVariable UUID projectId,
            @PathVariable UUID sessionId,@PathVariable UUID messageId,
            @Valid @RequestBody CreateAiProposalBatchRequest request) {
        UUID actorId=currentUsers.requireCurrentUser().getId();
        var result=batchCreation.create(new CreateAiProposalBatchCommand(projectId,sessionId,messageId,
                actorId,request.clientBatchId()));
        var response=new CreateAiProposalBatchResponse(batchQuery.detail(projectId,result.batch().getId()),result.replayed());
        if(result.replayed()) return ResponseEntity.ok(response);
        return ResponseEntity.created(URI.create("/api/projects/"+projectId+"/ai/proposal-batches/"+result.batch().getId())).body(response);
    }

    @GetMapping("/proposal-batches")
    public PagedAiProposalBatchResponse listBatches(@PathVariable UUID projectId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return batchQuery.list(projectId,page,size);
    }

    @GetMapping("/proposal-batches/{batchId}")
    public AiProposalBatchResponse batchDetail(@PathVariable UUID projectId,@PathVariable UUID batchId) {
        return batchQuery.detail(projectId,batchId);
    }

    @PostMapping("/proposal-batches/{batchId}/apply")
    public AiProposalBatchApplyAllResponse applyBatch(@PathVariable UUID projectId,
            @PathVariable UUID batchId,
            @Valid @RequestBody(required = false) AiProposalDecisionRequest request) {
        UUID actorId = currentUsers.requireCurrentUser().getId();
        String note = request == null ? null : request.note();
        var result = applyAll.apply(
                new AiProposalBatchApplyAllCommand(projectId, batchId, actorId, note));
        return new AiProposalBatchApplyAllResponse(result.batchId(), result.appliedCount(),
                result.finalModelRevision(), result.appliedAt());
    }

    @PostMapping("/sessions/{sessionId}/messages/{messageId}/proposals")
    public ResponseEntity<CreateAiProposalResponse> create(@PathVariable UUID projectId,
            @PathVariable UUID sessionId, @PathVariable UUID messageId,
            @Valid @RequestBody CreateAiProposalRequest request) {
        var result = service.create(projectId, sessionId, messageId, request);
        AiProposalResponse proposal = mapper.toResponse(result.proposal());
        CreateAiProposalResponse response = new CreateAiProposalResponse(proposal, result.replayed());
        if (result.replayed()) return ResponseEntity.ok(response);
        URI location = URI.create("/api/projects/" + projectId + "/ai/proposals/" + proposal.id());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/proposals")
    public PagedAiProposalResponse list(@PathVariable UUID projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(required = false) AiProposalStatus status,
            @RequestParam(required = false) UUID sessionId,
            @RequestParam(required = false) UUID modelId,
            @RequestParam(required = false) UUID diagramId,
            @RequestParam(required = false) AiProposalType proposalType) {
        return mapper.toPaged(service.list(projectId, status, sessionId, modelId, diagramId,
                proposalType, page, size));
    }

    @GetMapping("/proposals/{proposalId}")
    public AiProposalResponse detail(@PathVariable UUID projectId,
            @PathVariable UUID proposalId) {
        return mapper.toResponse(service.detail(projectId, proposalId));
    }

    @PostMapping("/proposals/{proposalId}/apply")
    public AiProposalResponse applyProposal(@PathVariable UUID projectId,
            @PathVariable UUID proposalId,
            @Valid @RequestBody AiProposalDecisionRequest request) {
        UUID actorId = currentUsers.requireCurrentUser().getId();
        return mapper.toResponse(decisions.apply(
                new AiProposalDecisionCommand(projectId, proposalId, actorId, request.note())));
    }

    @PostMapping("/proposals/{proposalId}/reject")
    public AiProposalResponse rejectProposal(@PathVariable UUID projectId,
            @PathVariable UUID proposalId,
            @Valid @RequestBody AiProposalDecisionRequest request) {
        UUID actorId = currentUsers.requireCurrentUser().getId();
        return mapper.toResponse(decisions.reject(
                new AiProposalDecisionCommand(projectId, proposalId, actorId, request.note())));
    }
}
