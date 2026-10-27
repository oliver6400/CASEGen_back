package com.casegen.ai.provider;

import com.casegen.ai.provider.gemini.GeminiAiProvider;
import com.casegen.ai.provider.ollama.OllamaAiProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(AiProviderProperties.class)
public class AiProviderConfiguration {

    @Bean
    OllamaAiProvider ollamaAiProvider(AiProviderProperties properties) {
        return new OllamaAiProvider(properties.ollama());
    }

    @Bean
    GeminiAiProvider geminiAiProvider(AiProviderProperties properties) {
        return new GeminiAiProvider(properties.gemini());
    }

    @Bean
    @Primary
    SelectedAiProvider selectedAiProvider(AiProviderProperties properties,
            OllamaAiProvider ollama, GeminiAiProvider gemini) {
        return new SelectedAiProvider(properties.provider(), ollama, gemini);
    }
}
