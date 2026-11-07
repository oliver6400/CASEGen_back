package com.casegen.ai.proposal.review;
import java.util.List;
public record AiReviewItem(String clientRef, AiReviewProposalType proposalType, String title,
        String rationale, List<String> dependencies, AiReviewPayload payload) {
    public AiReviewItem { dependencies = dependencies == null ? null : List.copyOf(dependencies); }
}
