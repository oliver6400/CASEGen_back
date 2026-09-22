package com.casegen.interoperability.image;

import static com.casegen.interoperability.image.DetectedClassDiagram.DetectionWarning;

import java.util.ArrayList;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class SelectedImageAnalysisProvider implements ImageAnalysisProvider {
    private final ImageAnalysisProvider selected;
    private final LocalImageAnalysisProvider local;
    private final boolean localFallbackEnabled;

    public SelectedImageAnalysisProvider(OpenAiClassDiagramImageAnalyzer openAi,
            GeminiImageAnalysisProvider gemini,
            LocalImageAnalysisProvider local,
            @Value("${casegen.image-import.provider:GEMINI}") String provider,
            @Value("${casegen.image-import.local-fallback-enabled:true}") boolean localFallbackEnabled) {
        String normalized = provider == null || provider.isBlank()
                ? "GEMINI" : provider.trim().toUpperCase(Locale.ROOT);
        this.selected = switch (normalized) {
            case "LOCAL" -> local;
            case "GEMINI" -> gemini;
            case "OPENAI" -> openAi;
            default -> throw new IllegalArgumentException(
                    "Unsupported IMAGE_IMPORT_PROVIDER. Expected LOCAL, GEMINI or OPENAI");
        };
        this.local = local;
        this.localFallbackEnabled = localFallbackEnabled;
    }

    @Override
    public DetectedClassDiagram analyze(byte[] image, String mimeType, String fileName) {
        try {
            return selected.analyze(image, mimeType, fileName);
        } catch (ImageImportException exception) {
            if (!shouldUseLocalFallback(exception)) throw exception;
            DetectedClassDiagram fallback = local.analyze(image, mimeType, fileName);
            var warnings = new ArrayList<>(fallback.warnings());
            warnings.add(new DetectionWarning("GEMINI_LOCAL_FALLBACK",
                    "Gemini no estuvo disponible. Se utilizó análisis local.", null));
            return new DetectedClassDiagram(fallback.suggestedModelName(),
                    fallback.detectedClassifiers(), fallback.detectedAttributes(),
                    fallback.detectedOperations(), fallback.detectedRelationships(),
                    warnings, fallback.confidence());
        }
    }

    @Override public boolean configured() { return selected.configured(); }
    @Override public String provider() { return selected.provider(); }
    @Override public String model() { return selected.model(); }
    @Override public Boolean localOcrAvailable() { return selected.localOcrAvailable(); }
    @Override public boolean fallbackLocalAvailable() {
        return "GEMINI".equals(selected.provider()) && localFallbackEnabled && local.configured();
    }

    private boolean shouldUseLocalFallback(ImageImportException exception) {
        if (!fallbackLocalAvailable()) return false;
        return switch (exception.getCode()) {
            case "GEMINI_QUOTA_EXCEEDED", "GEMINI_UNAVAILABLE" -> true;
            default -> false;
        };
    }
}
