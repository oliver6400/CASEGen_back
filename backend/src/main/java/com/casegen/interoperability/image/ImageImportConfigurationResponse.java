package com.casegen.interoperability.image;

public record ImageImportConfigurationResponse(
        boolean configured,
        String provider,
        String model,
        Boolean localOcrAvailable,
        boolean fallbackLocalAvailable) {
    public ImageImportConfigurationResponse(boolean configured, String provider, String model) {
        this(configured, provider, model, null, false);
    }

    public ImageImportConfigurationResponse(boolean configured, String provider, String model,
            Boolean localOcrAvailable) {
        this(configured, provider, model, localOcrAvailable, false);
    }
}
