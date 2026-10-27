package com.casegen.ai.provider.ollama;

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
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class OllamaAiProvider implements AiProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(OllamaAiProvider.class);
    private static final String NAME = "OLLAMA";
    private static final Pattern LEADING_THINK_BLOCKS = Pattern.compile(
            "\\A\\s*(?:<think>.*?</think>\\s*)+", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private final String baseUrl;
    private final String model;
    private final Duration timeout;
    private final HttpClient client;
    private final JsonMapper json;

    public OllamaAiProvider(AiProviderProperties.Ollama properties) {
        this(properties, HttpClient.newBuilder()
                .connectTimeout(validTimeout(properties.timeout())).build(),
                JsonMapper.builder().build());
    }

    OllamaAiProvider(AiProviderProperties.Ollama properties, HttpClient client, JsonMapper json) {
        this.baseUrl = trim(properties.baseUrl());
        this.model = trim(properties.model());
        this.timeout = validTimeout(properties.timeout());
        this.client = client;
        this.json = json;
    }

    @Override public String provider() { return NAME; }
    @Override public String model() { return model; }
    @Override public boolean configured() { return !baseUrl.isBlank() && !model.isBlank(); }

    @Override
    public AiProviderResult generate(AiPrompt prompt) {
        requireConfigured();
        long started = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder(chatUri())
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body(prompt))))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw httpError(response.statusCode());
            }
            JsonNode root = parseResponse(response.body());
            String content = extractFinalContent(
                    root.path("message").path("content").asText(""));
            if (content.isBlank()) throw invalidResponse();
            String finishReason = root.path("done_reason").asText("");
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
        List<Map<String, String>> messages = new ArrayList<>();
        if (!prompt.systemInstruction().isBlank()) {
            messages.add(Map.of("role", "system", "content", prompt.systemInstruction()));
        }
        for (AiPromptMessage message : prompt.messages()) {
            messages.add(Map.of("role", message.role().name().toLowerCase(),
                    "content", message.content()));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", false);
        body.put("think", false);
        return body;
    }

    private URI chatUri() {
        return URI.create(baseUrl.replaceAll("/+$", "") + "/api/chat");
    }

    private JsonNode parseResponse(String body) {
        try {
            return json.readTree(body);
        } catch (Exception exception) {
            throw invalidResponse();
        }
    }

    private String extractFinalContent(String content) {
        if (content == null) return "";
        String finalContent = LEADING_THINK_BLOCKS.matcher(content.trim())
                .replaceFirst("").trim();
        int lastClosingTag = finalContent.toLowerCase(Locale.ROOT).lastIndexOf("</think>");
        if (lastClosingTag >= 0) {
            finalContent = finalContent.substring(lastClosingTag + "</think>".length()).trim();
        }
        return finalContent;
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
        if (status >= 500) {
            return new AiProviderException(AiProviderErrorCode.UNAVAILABLE, NAME,
                    "AI provider is unavailable");
        }
        return invalidResponse();
    }

    private void requireConfigured() {
        if (!configured()) {
            throw new AiProviderException(AiProviderErrorCode.NOT_CONFIGURED, NAME,
                    "Ollama AI provider is not configured");
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
