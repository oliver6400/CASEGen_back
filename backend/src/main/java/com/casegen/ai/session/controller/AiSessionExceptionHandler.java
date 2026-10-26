package com.casegen.ai.session.controller;

import com.casegen.ai.session.exception.AiSessionErrorCode;
import com.casegen.ai.session.exception.AiSessionException;
import com.casegen.shared.api.ApiErrorResponse;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.casegen.ai.session.controller")
public class AiSessionExceptionHandler {

    @ExceptionHandler(AiSessionException.class)
    public ResponseEntity<ApiErrorResponse> handle(AiSessionException exception) {
        HttpStatus status = switch (exception.getCode()) {
            case PROJECT_NOT_FOUND, SESSION_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case SESSION_NOT_ACTIVE, AI_SESSION_CONCURRENT_START -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                exception.getCode().name(), exception.getMessage(), Instant.now()));
    }
}
