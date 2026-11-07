package com.casegen.ai.proposal.service;
import java.util.UUID;
public record AiProposalBatchApplyAllCommand(UUID projectId, UUID batchId,
        UUID actorUserId, String note) { }
