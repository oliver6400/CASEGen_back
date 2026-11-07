package com.casegen.ai.proposal.dto;

import com.casegen.ai.proposal.domain.AiProposalStatus;
import java.util.UUID;

public record AiProposalBatchDependencyResponse(UUID id, String title,
        AiProposalStatus status) { }
