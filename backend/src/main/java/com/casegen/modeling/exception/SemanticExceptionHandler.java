package com.casegen.modeling.exception;

import com.casegen.shared.api.ApiErrorResponse;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class SemanticExceptionHandler {

    @ExceptionHandler(SemanticException.class)
    public ResponseEntity<ApiErrorResponse> handleSemanticException(SemanticException exception) {
        return response(exception.getStatus(), exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Request validation failed");
        return response(HttpStatus.BAD_REQUEST, SemanticErrorCode.VALIDATION_ERROR, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableRequest() {
        return response(HttpStatus.BAD_REQUEST, SemanticErrorCode.INVALID_REQUEST,
                "Request body is malformed or contains an unsupported value");
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleOptimisticLocking() {
        return response(HttpStatus.CONFLICT, SemanticErrorCode.CONCURRENT_MODEL_MODIFICATION,
                "The UML model was modified concurrently");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity() {
        return response(HttpStatus.CONFLICT, SemanticErrorCode.DATA_INTEGRITY_VIOLATION,
                "The requested semantic change violates a data constraint");
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status, SemanticErrorCode code, String message) {
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(code.name(), message, Instant.now()));
    }
}
