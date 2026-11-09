package com.casegen.ai.provider;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "casegen.ai")
public record AiProviderProperties(
        Provider provider,
        Provider fallbackProvider,
        int maxMessageLength,
        int contextMessageLimit,
        int contextMaxChars,
        Ollama ollama,
        Gemini gemini,
        OpenAi openai
) {
    public enum Provider { OLLAMA, GEMINI, OPENAI }

    public record Ollama(String baseUrl, String model, Duration timeout) {
    }

    public record Gemini(String apiKey, String model, String baseUrl, Duration timeout) {
    }

    public record OpenAi(String apiKey, String model, String baseUrl, Duration timeout,
            String reasoningEffort) {
    }
}
