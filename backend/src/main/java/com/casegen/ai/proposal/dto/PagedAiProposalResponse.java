package com.casegen.ai.proposal.dto;

import java.util.List;

public record PagedAiProposalResponse(List<AiProposalResponse> content, int page, int size,
        long totalElements, int totalPages, boolean first, boolean last) {
}
