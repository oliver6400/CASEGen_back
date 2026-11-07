package com.casegen.ai.proposal.review;
import java.util.UUID;
public record GeneratedAiReview(AiReviewResult review, long baseModelRevision,
        Long baseDiagramRevision, UUID modelId, UUID diagramId, UUID sourceMessageId) { }
