package com.casegen.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class WorkflowConflictException extends ResponseStatusException {
    private final String code;
    public WorkflowConflictException(String code,String message) {
        super(HttpStatus.CONFLICT,message);this.code=code;
    }
    public String getCode() { return code; }
}
