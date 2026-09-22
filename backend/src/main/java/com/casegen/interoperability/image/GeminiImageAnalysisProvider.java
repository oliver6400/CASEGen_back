package com.casegen.interoperability.image;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class GeminiImageAnalysisProvider implements ImageAnalysisProvider {
    static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    static final String DEFAULT_MODEL = "gemini-2.5-flash";
    private static final Logger LOGGER = LoggerFactory.getLogger(GeminiImageAnalysisProvider.class);

    private final String apiKey;
    private final String model;
    private final URI generateContentUri;
    private final HttpClient client;
    private final JsonMapper json = JsonMapper.builder().build();

    public GeminiImageAnalysisProvider(
            @Value("${casegen.image-import.gemini.api-key:}") String apiKey,
            @Value("${casegen.image-import.gemini.model:gemini-2.5-flash}") String model,
            @Value("${casegen.image-import.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null || model.isBlank() ? DEFAULT_MODEL : model.trim();
        String root = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl.trim();
        this.generateContentUri = URI.create(root.replaceAll("/+$", "") + "/models/"
                + URLEncoder.encode(this.model, StandardCharsets.UTF_8) + ":generateContent");
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    }

    @Override public boolean configured() { return !apiKey.isBlank(); }
    @Override public String provider() { return "GEMINI"; }
    @Override public String model() { return model; }

    @Override
    public DetectedClassDiagram analyze(byte[] image, String mimeType, String fileName) {
        if (!configured()) {
            throw new ImageImportException("IMAGE_ANALYSIS_NOT_CONFIGURED", HttpStatus.SERVICE_UNAVAILABLE,
                    "El servicio de análisis de imágenes no está configurado.");
        }
        try {
            String data = Base64.getEncoder().encodeToString(image);
            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(Map.of("role", "user", "parts", List.of(
                            Map.of("text", UmlImageAnalysisContract.PROMPT + "\nFilename: " + safe(fileName)),
                            Map.of("inline_data", Map.of("mime_type", mimeType, "data", data))))),
                    "generationConfig", Map.of(
                            "responseMimeType", "application/json",
                            "responseJsonSchema", UmlImageAnalysisContract.schema()));
            HttpRequest request = HttpRequest.newBuilder(generateContentUri)
                    .timeout(Duration.ofMinutes(2))
                    .header("x-goog-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(requestBody)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw providerError(response.statusCode(), response.body());
            }
            JsonNode root = json.readTree(response.body());
            String output = root.path("candidates").path(0).path("content").path("parts").path(0)
                    .path("text").asText("");
            if (output.isBlank()) throw DetectedClassDiagramResponseValidator.invalid("GEMINI", null);
            return DetectedClassDiagramResponseValidator.parse(json, output, "GEMINI");
        } catch (ImageImportException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable(exception);
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    private ImageImportException providerError(int status, String body) {
        ProviderError error = safeProviderError(body);
        LOGGER.warn("Image analysis provider=GEMINI model={} httpStatus={} providerCode={} providerMessage={}",
                model, status, error.code(), error.message());
        return switch (status) {
            case 400 -> new ImageImportException("GEMINI_INVALID_REQUEST", HttpStatus.BAD_REQUEST,
                    "Gemini rechazó la solicitud de análisis por ser inválida.");
            case 401, 403 -> new ImageImportException("GEMINI_CONFIGURATION_INVALID",
                    HttpStatus.SERVICE_UNAVAILABLE, "La configuración del servicio Gemini no es válida.");
            case 429 -> new ImageImportException("GEMINI_QUOTA_EXCEEDED", HttpStatus.TOO_MANY_REQUESTS,
                    "Gemini alcanzó temporalmente su límite o cuota.");
            default -> new ImageImportException("GEMINI_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE,
                    "El proveedor de análisis de imágenes no está disponible temporalmente.");
        };
    }

    private ProviderError safeProviderError(String body) {
        try {
            JsonNode error = json.readTree(body).path("error");
            String code = safe(error.path("status").asText("UNKNOWN"));
            String message = safe(error.path("message").asText("Provider request failed"));
            return new ProviderError(code, message);
        } catch (Exception exception) {
            return new ProviderError("UNKNOWN", "Provider request failed");
        }
    }

    private String safe(String value) {
        String sanitized = value == null ? "" : value.replace(apiKey, "[redacted]")
                .replaceAll("[\\r\\n\\t]+", " ").trim();
        return sanitized.length() > 300 ? sanitized.substring(0, 300) : sanitized;
    }

    private ImageImportException unavailable(Exception cause) {
        LOGGER.warn("Image analysis provider=GEMINI model={} failed without HTTP response", model);
        return new ImageImportException("GEMINI_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE,
                "El proveedor de análisis de imágenes no está disponible temporalmente.", cause);
    }

    private record ProviderError(String code, String message) {}
}
