package com.casegen.ai.proposal.validation;

public class AiProposalValidationException extends RuntimeException {
    private final AiProposalValidationErrorCode code;

    public AiProposalValidationException(AiProposalValidationErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public AiProposalValidationException(AiProposalValidationErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public AiProposalValidationErrorCode getCode() {
        return code;
    }
}
