package com.casegen.auth.exception;

import com.casegen.shared.api.ApiErrorResponse;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthException(AuthException exception) {
        return ResponseEntity.status(exception.getStatus()).body(new ApiErrorResponse(
                exception.getCode().name(), exception.getMessage(), Instant.now()));
    }
}
