package com.casegen.ai.session.dto;

import java.util.List;

public record PagedAiSessionResponse(
        List<AiSessionSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {
}
