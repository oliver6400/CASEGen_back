package com.casegen.interoperability.image;

import com.casegen.shared.api.ApiErrorResponse;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.casegen.interoperability.image")
public class ImageImportExceptionHandler {
    @ExceptionHandler(ImageImportException.class)
    public ResponseEntity<ApiErrorResponse> handle(ImageImportException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(new ApiErrorResponse(exception.getCode(), exception.getMessage(), Instant.now()));
    }
}
