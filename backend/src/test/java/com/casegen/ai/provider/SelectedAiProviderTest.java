package com.casegen.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class SelectedAiProviderTest {

    private final AiPrompt prompt = new AiPrompt("system",
            List.of(new AiPromptMessage(AiPromptRole.USER, "question")));

    @Test
    void selectsOllamaExplicitly() {
        AiProvider selected = new SelectedAiProvider(AiProviderProperties.Provider.OLLAMA,
                AiProviderProperties.Provider.OLLAMA,
                provider("OLLAMA", true), provider("GEMINI", true), provider("OPENAI", true));

        assertEquals("OLLAMA", selected.provider());
        assertEquals("OLLAMA answer", selected.generate(prompt).content());
    }

    @Test
    void selectsGeminiExplicitly() {
        AiProvider selected = new SelectedAiProvider(AiProviderProperties.Provider.GEMINI,
                AiProviderProperties.Provider.OLLAMA,
                provider("OLLAMA", true), provider("GEMINI", true), provider("OPENAI", true));

        assertEquals("GEMINI", selected.provider());
        assertEquals("GEMINI answer", selected.generate(prompt).content());
    }

    @Test
    void selectsOpenAiExplicitly() {
        AiProvider selected = new SelectedAiProvider(AiProviderProperties.Provider.OPENAI,
                AiProviderProperties.Provider.OLLAMA,
                provider("OLLAMA", true), provider("GEMINI", true), provider("OPENAI", true));

        assertEquals("OPENAI", selected.provider());
        assertEquals("OPENAI answer", selected.generate(prompt).content());
    }

    @Test
    void selectedUnconfiguredProviderFailsWithControlledError() {
        AiProvider selected = new SelectedAiProvider(AiProviderProperties.Provider.GEMINI,
                AiProviderProperties.Provider.OLLAMA,
                provider("OLLAMA", false), provider("GEMINI", false), provider("OPENAI", true));

        AiProviderException error = assertThrows(
                AiProviderException.class, () -> selected.generate(prompt));

        assertEquals(AiProviderErrorCode.NOT_CONFIGURED, error.getCode());
        assertEquals("GEMINI", error.getProvider());
    }

    @Test
    void unavailableOpenAiFallsBackToOllama() {
        AiProvider openAi = providerFailure("OPENAI", AiProviderErrorCode.UNAVAILABLE);
        AiProvider selected = new SelectedAiProvider(AiProviderProperties.Provider.OPENAI,
                AiProviderProperties.Provider.OLLAMA,
                provider("OLLAMA", true), provider("GEMINI", true), openAi);

        AiProviderResult result = selected.generate(prompt);

        assertEquals("OLLAMA", result.provider());
        assertEquals("OLLAMA answer", result.content());
    }

    @Test
    void invalidOpenAiResponseDoesNotFallBack() {
        AiProvider openAi = providerFailure("OPENAI", AiProviderErrorCode.INVALID_RESPONSE);
        AiProvider selected = new SelectedAiProvider(AiProviderProperties.Provider.OPENAI,
                AiProviderProperties.Provider.OLLAMA,
                provider("OLLAMA", true), provider("GEMINI", true), openAi);

        AiProviderException error = assertThrows(AiProviderException.class,
                () -> selected.generate(prompt));

        assertEquals(AiProviderErrorCode.INVALID_RESPONSE, error.getCode());
        assertEquals("OPENAI", error.getProvider());
    }

    private AiProvider provider(String name, boolean configured) {
        return new AiProvider() {
            @Override public AiProviderResult generate(AiPrompt value) {
                return new AiProviderResult(name + " answer", name, "model", null);
            }
            @Override public String provider() { return name; }
            @Override public String model() { return "model"; }
            @Override public boolean configured() { return configured; }
        };
    }

    private AiProvider providerFailure(String name, AiProviderErrorCode code) {
        return new AiProvider() {
            @Override public AiProviderResult generate(AiPrompt value) {
                throw new AiProviderException(code, name, "controlled failure");
            }
            @Override public String provider() { return name; }
            @Override public String model() { return "model"; }
            @Override public boolean configured() { return true; }
        };
    }
}
