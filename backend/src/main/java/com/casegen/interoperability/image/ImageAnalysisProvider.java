package com.casegen.interoperability.image;

public interface ImageAnalysisProvider {
    DetectedClassDiagram analyze(byte[] image, String mimeType, String fileName);

    boolean configured();

    String provider();

    String model();

    default Boolean localOcrAvailable() {
        return null;
    }

    default boolean fallbackLocalAvailable() {
        return false;
    }
}
