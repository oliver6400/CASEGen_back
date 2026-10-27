package com.casegen.ai.provider;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "casegen.ai")
public record AiProviderProperties(
        Provider provider,
        int maxMessageLength,
        int contextMessageLimit,
        int contextMaxChars,
        Ollama ollama,
        Gemini gemini
) {
    public enum Provider { OLLAMA, GEMINI }

    public record Ollama(String baseUrl, String model, Duration timeout) {
    }

    public record Gemini(String apiKey, String model, String baseUrl, Duration timeout) {
    }
}
