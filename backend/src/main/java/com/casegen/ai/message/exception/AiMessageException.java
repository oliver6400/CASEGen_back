package com.casegen.ai.message.exception;

public class AiMessageException extends RuntimeException {
    private final AiMessageErrorCode code;

    public AiMessageException(AiMessageErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public AiMessageErrorCode getCode() { return code; }
}
