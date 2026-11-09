package com.casegen.ai.provider;

import java.util.Map;
import java.util.Objects;

public final class SelectedAiProvider implements AiProvider {

    private final AiProvider primary;
    private final AiProvider fallback;

    public SelectedAiProvider(AiProviderProperties.Provider selected,
            AiProviderProperties.Provider fallbackProvider, AiProvider ollama,
            AiProvider gemini, AiProvider openAi) {
        Objects.requireNonNull(selected, "selected provider is required");
        Map<AiProviderProperties.Provider, AiProvider> providers = Map.of(
                AiProviderProperties.Provider.OLLAMA, ollama,
                AiProviderProperties.Provider.GEMINI, gemini,
                AiProviderProperties.Provider.OPENAI, openAi
        );
        this.primary = providers.get(selected);
        this.fallback = fallbackProvider == null || fallbackProvider == selected
                ? null : providers.get(fallbackProvider);
    }

    @Override
    public AiProviderResult generate(AiPrompt prompt) {
        try {
            return generate(primary, prompt);
        } catch (AiProviderException exception) {
            if (fallback == null || !fallback.configured() || !fallbackAllowed(exception)) {
                throw exception;
            }
            return generate(fallback, prompt);
        }
    }

    private AiProviderResult generate(AiProvider provider, AiPrompt prompt) {
        if (!provider.configured()) {
            throw new AiProviderException(AiProviderErrorCode.NOT_CONFIGURED, provider.provider(),
                    "The selected AI provider is not configured");
        }
        return provider.generate(prompt);
    }

    private boolean fallbackAllowed(AiProviderException exception) {
        return switch (exception.getCode()) {
            case NOT_CONFIGURED, UNAVAILABLE, TIMEOUT, RATE_LIMITED -> true;
            case INVALID_RESPONSE -> false;
        };
    }

    @Override public String provider() { return primary.provider(); }
    @Override public String model() { return primary.model(); }
    @Override public boolean configured() {
        return primary.configured() || fallback != null && fallback.configured();
    }
}
