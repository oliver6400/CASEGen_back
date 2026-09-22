package com.casegen.interoperability.xmi;

import org.springframework.http.HttpStatus;

public class XmiImportException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public XmiImportException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public XmiImportException(String code, HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.status = status;
    }

    public String getCode() { return code; }
    public HttpStatus getStatus() { return status; }
}
