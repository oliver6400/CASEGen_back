package com.casegen.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AiProviderDefaultConfigurationTest {

    @Test
    void deliveryDefaultsToGeminiWhileKeepingOllamaConfiguration() throws IOException {
        try (var input = getClass().getResourceAsStream("/application.yaml")) {
            assertThat(input).isNotNull();
            String configuration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(configuration).contains("provider: ${AI_PROVIDER:GEMINI}",
                    "ollama:", "gemini:");
        }
    }
}
