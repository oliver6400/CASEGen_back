package com.casegen.ai.provider.openai;

import com.casegen.ai.provider.AiPrompt;
import com.casegen.ai.provider.AiPromptMessage;
import com.casegen.ai.provider.AiProvider;
import com.casegen.ai.provider.AiProviderErrorCode;
import com.casegen.ai.provider.AiProviderException;
import com.casegen.ai.provider.AiProviderProperties;
import com.casegen.ai.provider.AiProviderResult;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** OpenAI Responses API adapter for CASEGen's provider-neutral AI prompts. */
public final class OpenAiAiProvider implements AiProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenAiAiProvider.class);
    private static final String NAME = "OPENAI";
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final Duration timeout;
    private final String reasoningEffort;
    private final HttpClient client;
    private final JsonMapper json;

    public OpenAiAiProvider(AiProviderProperties.OpenAi properties) {
        this(properties, HttpClient.newBuilder()
                .connectTimeout(validTimeout(properties.timeout())).build(),
                JsonMapper.builder().build());
    }

    OpenAiAiProvider(AiProviderProperties.OpenAi properties, HttpClient client, JsonMapper json) {
        this.apiKey = trim(properties.apiKey());
        this.model = trim(properties.model());
        this.baseUrl = trim(properties.baseUrl());
        this.timeout = validTimeout(properties.timeout());
        this.reasoningEffort = validReasoningEffort(properties.reasoningEffort());
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
            HttpRequest request = HttpRequest.newBuilder(responsesUri())
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body(prompt))))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw httpError(response.statusCode());
            }
            JsonNode root = parseResponse(response.body());
            String content = outputText(root);
            if (content.isBlank()) throw invalidResponse();
            String finishReason = root.path("status").asText("");
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
        List<Map<String, Object>> input = new ArrayList<>();
        for (AiPromptMessage message : prompt.messages()) {
            input.add(Map.of("role", message.role().name().toLowerCase(Locale.ROOT),
                    "content", message.content()));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("store", false);
        if (!prompt.systemInstruction().isBlank()) {
            body.put("instructions", prompt.systemInstruction());
        }
        body.put("input", input);
        body.put("reasoning", Map.of("effort", reasoningEffort));
        return body;
    }

    private String outputText(JsonNode root) {
        String direct = root.path("output_text").asText("").trim();
        if (!direct.isBlank()) return direct;
        List<String> text = new ArrayList<>();
        for (JsonNode item : root.path("output")) {
            for (JsonNode content : item.path("content")) {
                if ("output_text".equals(content.path("type").asText())) {
                    String value = content.path("text").asText("").trim();
                    if (!value.isBlank()) text.add(value);
                }
            }
        }
        return String.join("\n", text).trim();
    }

    private JsonNode parseResponse(String responseBody) {
        try {
            return json.readTree(responseBody);
        } catch (Exception exception) {
            throw invalidResponse();
        }
    }

    private URI responsesUri() {
        String normalized = baseUrl.replaceAll("/+$", "");
        return URI.create(normalized.endsWith("/responses") ? normalized : normalized + "/responses");
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
                    "OpenAI provider configuration is invalid");
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
                    "OpenAI provider is not configured");
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

    private static String validReasoningEffort(String value) {
        String normalized = trim(value).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "none", "low", "medium", "high", "xhigh", "max" -> normalized;
            default -> "medium";
        };
    }

    private static Duration validTimeout(Duration value) {
        return value == null || value.isZero() || value.isNegative() ? Duration.ofMinutes(2) : value;
    }

    private static String trim(String value) { return value == null ? "" : value.trim(); }
}
