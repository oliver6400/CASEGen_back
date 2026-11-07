package com.casegen.ai.proposal.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateAiProposalBatchRequest(@NotNull UUID clientBatchId) { }
