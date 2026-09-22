package com.casegen.interoperability.image;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** One-shot vision adapter. It is deliberately not part of the semantic domain. */
@Component
public class OpenAiClassDiagramImageAnalyzer implements ImageAnalysisProvider {
    static final String DEFAULT_API_URL = "https://api.openai.com/v1/responses";
    static final String DEFAULT_MODEL = "gpt-4o-mini";
    private final String apiKey;
    private final String model;
    private final URI apiUri;
    private final HttpClient client;
    private final JsonMapper json = JsonMapper.builder().build();

    public OpenAiClassDiagramImageAnalyzer(
            @Value("${casegen.image-import.api-key:}") String apiKey,
            @Value("${casegen.image-import.model:gpt-4o-mini}") String model,
            @Value("${casegen.image-import.api-url:https://api.openai.com/v1/responses}") String apiUrl) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null || model.isBlank() ? DEFAULT_MODEL : model.trim();
        this.apiUri = URI.create(apiUrl == null || apiUrl.isBlank() ? DEFAULT_API_URL : apiUrl.trim());
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    }

    @Override
    public boolean configured() {
        return !apiKey.isBlank();
    }

    @Override
    public String provider() {
        return "OPENAI";
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public DetectedClassDiagram analyze(byte[] image, String mediaType, String fileName) {
        if (!configured()) {
            throw new ImageImportException("IMAGE_ANALYSIS_NOT_CONFIGURED", HttpStatus.SERVICE_UNAVAILABLE,
                    "El servicio de análisis de imágenes no está configurado.");
        }
        try {
            String dataUrl = "data:" + mediaType + ";base64," + Base64.getEncoder().encodeToString(image);
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("model", model);
            request.put("store", false);
            request.put("input", List.of(Map.of("role", "user", "content", List.of(
                    Map.of("type", "input_text", "text", UmlImageAnalysisContract.PROMPT + "\nFilename: " + safe(fileName)),
                    Map.of("type", "input_image", "image_url", dataUrl, "detail", "high")))));
            request.put("text", Map.of("format", Map.of(
                    "type", "json_schema", "name", "detected_class_diagram",
                    "strict", true, "schema", UmlImageAnalysisContract.schema())));

            HttpRequest httpRequest = HttpRequest.newBuilder(apiUri)
                    .timeout(Duration.ofMinutes(2))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(request)))
                    .build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ImageImportException("IMAGE_ANALYSIS_UNAVAILABLE", HttpStatus.BAD_GATEWAY,
                        "The image analysis provider rejected the request (HTTP " + response.statusCode() + ")");
            }
            JsonNode root = json.readTree(response.body());
            String output = outputText(root);
            if (output == null || output.isBlank()) {
                throw new ImageImportException("IMAGE_ANALYSIS_EMPTY", HttpStatus.UNPROCESSABLE_ENTITY,
                        "No se pudieron identificar suficientes elementos UML.");
            }
            return DetectedClassDiagramResponseValidator.parse(json, output, "OPENAI");
        } catch (ImageImportException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable(exception);
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    private ImageImportException unavailable(Exception cause) {
        return new ImageImportException("IMAGE_ANALYSIS_UNAVAILABLE", HttpStatus.BAD_GATEWAY,
                "No fue posible analizar la imagen. Inténtelo nuevamente.", cause);
    }

    private String outputText(JsonNode root) {
        for (JsonNode item : root.path("output")) {
            for (JsonNode content : item.path("content")) {
                if ("output_text".equals(content.path("type").asText())) return content.path("text").asText();
            }
        }
        return null;
    }

    private String safe(String fileName) {
        return fileName == null ? "uploaded-image" : fileName.replaceAll("[^A-Za-z0-9._ -]", "_");
    }

}
