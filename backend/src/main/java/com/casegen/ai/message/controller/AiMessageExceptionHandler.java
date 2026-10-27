package com.casegen.ai.message.controller;

import com.casegen.ai.context.AiContextErrorCode;
import com.casegen.ai.context.AiContextException;
import com.casegen.ai.message.exception.AiMessageException;
import com.casegen.ai.provider.AiProviderException;
import com.casegen.shared.api.ApiErrorResponse;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = AiMessageController.class)
public class AiMessageExceptionHandler {

    @ExceptionHandler(AiMessageException.class)
    ResponseEntity<ApiErrorResponse> handleMessage(AiMessageException exception) {
        HttpStatus status = switch (exception.getCode()) {
            case SESSION_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case SESSION_NOT_ACTIVE, CLIENT_MESSAGE_ID_REUSED, MESSAGE_CONCURRENT_WRITE ->
                    HttpStatus.CONFLICT;
            case INVALID_CONTEXT -> HttpStatus.NOT_FOUND;
            case MESSAGE_TOO_LONG, MESSAGE_INVALID -> HttpStatus.BAD_REQUEST;
        };
        return error(status, exception.getCode().name(), exception.getMessage());
    }

    @ExceptionHandler(AiContextException.class)
    ResponseEntity<ApiErrorResponse> handleContext(AiContextException exception) {
        HttpStatus status = switch (exception.getCode()) {
            case PROJECT_NOT_FOUND, SESSION_NOT_FOUND, MODEL_NOT_FOUND, DIAGRAM_NOT_FOUND ->
                    HttpStatus.NOT_FOUND;
            case SESSION_NOT_ACTIVE, SESSION_PROJECT_MISMATCH, MODEL_PROJECT_MISMATCH,
                    DIAGRAM_REQUIRES_MODEL, DIAGRAM_MODEL_MISMATCH -> HttpStatus.CONFLICT;
            case MESSAGE_INVALID -> HttpStatus.BAD_REQUEST;
        };
        return error(status, exception.getCode().name(), exception.getMessage());
    }

    @ExceptionHandler(AiProviderException.class)
    ResponseEntity<ApiErrorResponse> handleProvider(AiProviderException exception) {
        HttpStatus status = switch (exception.getCode()) {
            case NOT_CONFIGURED, UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case INVALID_RESPONSE -> HttpStatus.BAD_GATEWAY;
        };
        return error(status, exception.getCode().name(), safeProviderMessage(exception));
    }

    private String safeProviderMessage(AiProviderException exception) {
        return switch (exception.getCode()) {
            case NOT_CONFIGURED -> "The selected AI provider is not configured";
            case UNAVAILABLE -> "The AI provider is unavailable";
            case TIMEOUT -> "The AI provider timed out";
            case RATE_LIMITED -> "The AI provider rate limit was reached";
            case INVALID_RESPONSE -> "The AI provider returned an invalid response";
        };
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(code, message, Instant.now()));
    }
}
