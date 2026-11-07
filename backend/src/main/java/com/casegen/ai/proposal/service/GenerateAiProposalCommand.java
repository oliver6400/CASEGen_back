package com.casegen.ai.proposal.service;

import java.util.UUID;

public record GenerateAiProposalCommand(UUID projectId, UUID sessionId,
        UUID sourceAssistantMessageId) {
}
