package com.casegen.ai.proposal.review;
import java.util.UUID;
public record GenerateAiReviewCommand(UUID projectId,UUID sessionId,UUID sourceMessageId) { }
