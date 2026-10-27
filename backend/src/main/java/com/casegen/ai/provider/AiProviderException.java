package com.casegen.ai.provider;

public class AiProviderException extends RuntimeException {

    private final AiProviderErrorCode code;
    private final String provider;

    public AiProviderException(AiProviderErrorCode code, String provider, String message) {
        super(message);
        this.code = code;
        this.provider = provider;
    }

    public AiProviderException(AiProviderErrorCode code, String provider, String message,
            Throwable cause) {
        super(message, cause);
        this.code = code;
        this.provider = provider;
    }

    public AiProviderErrorCode getCode() { return code; }
    public String getProvider() { return provider; }
}
