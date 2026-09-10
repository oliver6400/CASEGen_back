package com.casegen.modeling.exception;

import org.springframework.http.HttpStatus;

public class SemanticException extends RuntimeException {

    private final SemanticErrorCode code;
    private final HttpStatus status;

    public SemanticException(SemanticErrorCode code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public SemanticErrorCode getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
