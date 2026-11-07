package com.casegen.ai.proposal.review;
import java.util.List;
public record AiReviewResult(String reviewTitle, String summary, List<AiReviewItem> items,
        String generatedByProvider) {
    public AiReviewResult { items = items == null ? null : List.copyOf(items); }
}
