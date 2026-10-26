package com.casegen.ai.session.exception;

public class AiSessionException extends RuntimeException {
    private final AiSessionErrorCode code;

    public AiSessionException(AiSessionErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public AiSessionErrorCode getCode() {
        return code;
    }
}
