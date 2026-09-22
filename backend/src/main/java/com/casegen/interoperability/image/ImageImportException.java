package com.casegen.interoperability.image;

import org.springframework.http.HttpStatus;

public class ImageImportException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public ImageImportException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public ImageImportException(String code, HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.status = status;
    }

    public String getCode() { return code; }
    public HttpStatus getStatus() { return status; }
}
