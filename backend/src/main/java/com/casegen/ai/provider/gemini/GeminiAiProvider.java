package com.casegen.ai.provider.gemini;

import com.casegen.ai.provider.AiPrompt;
import com.casegen.ai.provider.AiPromptMessage;
import com.casegen.ai.provider.AiPromptRole;
import com.casegen.ai.provider.AiProvider;
import com.casegen.ai.provider.AiProviderErrorCode;
import com.casegen.ai.provider.AiProviderException;
import com.casegen.ai.provider.AiProviderProperties;
import com.casegen.ai.provider.AiProviderResult;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class GeminiAiProvider implements AiProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeminiAiProvider.class);
    private static final String NAME = "GEMINI";
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final Duration timeout;
    private final HttpClient client;
    private final JsonMapper json;

    public GeminiAiProvider(AiProviderProperties.Gemini properties) {
        this(properties, HttpClient.newBuilder()
                .connectTimeout(validTimeout(properties.timeout())).build(),
                JsonMapper.builder().build());
    }

    GeminiAiProvider(AiProviderProperties.Gemini properties, HttpClient client, JsonMapper json) {
        this.apiKey = trim(properties.apiKey());
        this.model = trim(properties.model());
        this.baseUrl = trim(properties.baseUrl());
        this.timeout = validTimeout(properties.timeout());
        this.client = client;
        this.json = json;
    }

    @Override public String provider() { return NAME; }
    @Override public String model() { return model; }
    @Override public boolean configured() {
        return !apiKey.isBlank() && !model.isBlank() && !baseUrl.isBlank();
    }

    @Override
    public AiProviderResult generate(AiPrompt prompt) {
        requireConfigured();
        long started = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder(generateContentUri())
                    .timeout(timeout)
                    .header("x-goog-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body(prompt))))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw httpError(response.statusCode());
            }
            JsonNode root = parseResponse(response.body());
            JsonNode candidate = root.path("candidates").path(0);
            String content = finalText(candidate.path("content").path("parts"));
            if (content.isBlank()) throw invalidResponse();
            String finishReason = candidate.path("finishReason").asText("");
            logSuccess(started);
            return new AiProviderResult(content, NAME, model,
                    finishReason.isBlank() ? null : finishReason);
        } catch (AiProviderException exception) {
            throw exception;
        } catch (HttpTimeoutException exception) {
            throw error(AiProviderErrorCode.TIMEOUT, "AI provider request timed out", exception);
        } catch (ConnectException exception) {
            throw error(AiProviderErrorCode.UNAVAILABLE, "AI provider is unavailable", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw error(AiProviderErrorCode.UNAVAILABLE, "AI provider request was interrupted", exception);
        } catch (IOException | RuntimeException exception) {
            throw error(AiProviderErrorCode.UNAVAILABLE, "AI provider is unavailable", exception);
        }
    }

    private Map<String, Object> body(AiPrompt prompt) {
        List<Map<String, Object>> contents = new ArrayList<>();
        for (AiPromptMessage message : prompt.messages()) {
            contents.add(Map.of(
                    "role", message.role() == AiPromptRole.ASSISTANT ? "model" : "user",
                    "parts", List.of(Map.of("text", message.content()))));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        if (!prompt.systemInstruction().isBlank()) {
            body.put("system_instruction", Map.of(
                    "parts", List.of(Map.of("text", prompt.systemInstruction()))));
        }
        body.put("contents", contents);
        return body;
    }

    private String finalText(JsonNode parts) {
        List<String> text = new ArrayList<>();
        for (JsonNode part : parts) {
            if (!part.path("thought").asBoolean(false)) {
                String value = part.path("text").asText("").trim();
                if (!value.isBlank()) text.add(value);
            }
        }
        return String.join("\n", text).trim();
    }

    private JsonNode parseResponse(String body) {
        try {
            return json.readTree(body);
        } catch (Exception exception) {
            throw invalidResponse();
        }
    }

    private URI generateContentUri() {
        return URI.create(baseUrl.replaceAll("/+$", "") + "/models/"
                + URLEncoder.encode(model, StandardCharsets.UTF_8) + ":generateContent");
    }

    private AiProviderException httpError(int status) {
        LOGGER.warn("AI provider={} model={} httpStatus={}", NAME, model, status);
        if (status == 408 || status == 504) {
            return new AiProviderException(AiProviderErrorCode.TIMEOUT, NAME,
                    "AI provider request timed out");
        }
        if (status == 429) {
            return new AiProviderException(AiProviderErrorCode.RATE_LIMITED, NAME,
                    "AI provider rate limit reached");
        }
        if (status == 401 || status == 403) {
            return new AiProviderException(AiProviderErrorCode.NOT_CONFIGURED, NAME,
                    "Gemini AI provider configuration is invalid");
        }
        if (status >= 500) {
            return new AiProviderException(AiProviderErrorCode.UNAVAILABLE, NAME,
                    "AI provider is unavailable");
        }
        return invalidResponse();
    }

    private void requireConfigured() {
        if (!configured()) {
            throw new AiProviderException(AiProviderErrorCode.NOT_CONFIGURED, NAME,
                    "Gemini AI provider is not configured");
        }
    }

    private AiProviderException invalidResponse() {
        return new AiProviderException(AiProviderErrorCode.INVALID_RESPONSE, NAME,
                "AI provider returned an invalid response");
    }

    private AiProviderException error(AiProviderErrorCode code, String message, Exception cause) {
        LOGGER.warn("AI provider={} model={} failed code={}", NAME, model, code);
        return new AiProviderException(code, NAME, message, cause);
    }

    private void logSuccess(long started) {
        LOGGER.info("AI provider={} model={} durationMs={}", NAME, model,
                (System.nanoTime() - started) / 1_000_000);
    }

    private static Duration validTimeout(Duration value) {
        return value == null || value.isZero() || value.isNegative() ? Duration.ofMinutes(2) : value;
    }

    private static String trim(String value) { return value == null ? "" : value.trim(); }
}
