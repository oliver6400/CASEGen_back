package com.casegen.ai.proposal.service;

public class AiProposalGenerationException extends RuntimeException {
    private final AiProposalGenerationErrorCode code;

    public AiProposalGenerationException(AiProposalGenerationErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public AiProposalGenerationException(AiProposalGenerationErrorCode code, String message,
            Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public AiProposalGenerationErrorCode getCode() {
        return code;
    }
}
