package com.casegen.interoperability.xmi;

import com.casegen.shared.api.ApiErrorResponse;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class XmiImportExceptionHandler {
    @ExceptionHandler(XmiImportException.class)
    public ResponseEntity<ApiErrorResponse> handle(XmiImportException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(new ApiErrorResponse(exception.getCode(), exception.getMessage(), Instant.now()));
    }
}
