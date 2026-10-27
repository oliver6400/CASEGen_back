package com.casegen.ai.message.dto;

import java.util.List;

public record PagedAiMessageResponse(
        List<AiMessageResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}
