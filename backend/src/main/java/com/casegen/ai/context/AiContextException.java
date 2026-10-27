package com.casegen.ai.context;

public class AiContextException extends RuntimeException {

    private final AiContextErrorCode code;

    public AiContextException(AiContextErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public AiContextErrorCode getCode() { return code; }
}
