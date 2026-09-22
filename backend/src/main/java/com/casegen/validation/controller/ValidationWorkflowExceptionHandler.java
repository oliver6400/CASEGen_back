package com.casegen.validation.controller;

import com.casegen.shared.api.ApiErrorResponse;
import com.casegen.shared.exception.WorkflowConflictException;
import java.time.Instant;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages={"com.casegen.validation.controller","com.casegen.versioning.controller"})
public class ValidationWorkflowExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handle(ResponseStatusException exception) {
        String reason=exception.getReason();
        String code=exception instanceof WorkflowConflictException conflict?conflict.getCode():
                exception.getStatusCode().value()==409?"VALIDATION_WORKFLOW_CONFLICT":"VALIDATION_WORKFLOW_ERROR";
        return ResponseEntity.status(exception.getStatusCode()).body(new ApiErrorResponse(code,
                reason==null?"Validation/version request could not be completed":reason,Instant.now()));
    }
}
