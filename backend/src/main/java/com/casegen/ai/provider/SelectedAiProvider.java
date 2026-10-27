package com.casegen.ai.provider;

import java.util.Map;
import java.util.Objects;

public final class SelectedAiProvider implements AiProvider {

    private final AiProvider delegate;

    public SelectedAiProvider(AiProviderProperties.Provider selected, AiProvider ollama,
            AiProvider gemini) {
        Objects.requireNonNull(selected, "selected provider is required");
        this.delegate = Map.of(
                AiProviderProperties.Provider.OLLAMA, ollama,
                AiProviderProperties.Provider.GEMINI, gemini
        ).get(selected);
    }

    @Override
    public AiProviderResult generate(AiPrompt prompt) {
        if (!configured()) {
            throw new AiProviderException(AiProviderErrorCode.NOT_CONFIGURED, provider(),
                    "The selected AI provider is not configured");
        }
        return delegate.generate(prompt);
    }

    @Override public String provider() { return delegate.provider(); }
    @Override public String model() { return delegate.model(); }
    @Override public boolean configured() { return delegate.configured(); }
}
