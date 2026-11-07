package com.casegen.ai.proposal.service;

public class AiProposalException extends RuntimeException {
    private final AiProposalErrorCode code;

    public AiProposalException(AiProposalErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public AiProposalException(AiProposalErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public AiProposalErrorCode getCode() {
        return code;
    }
}
