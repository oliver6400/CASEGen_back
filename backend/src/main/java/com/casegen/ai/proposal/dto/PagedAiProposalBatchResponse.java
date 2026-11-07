package com.casegen.ai.proposal.dto;

import java.util.List;

public record PagedAiProposalBatchResponse(List<AiProposalBatchResponse> content,
        int page, int size, long totalElements, int totalPages, boolean first, boolean last) { }
