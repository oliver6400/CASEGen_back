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
                provider("OLLAMA", true), provider("GEMINI", true));

        assertEquals("OLLAMA", selected.provider());
        assertEquals("OLLAMA answer", selected.generate(prompt).content());
    }

    @Test
    void selectsGeminiExplicitly() {
        AiProvider selected = new SelectedAiProvider(AiProviderProperties.Provider.GEMINI,
                provider("OLLAMA", true), provider("GEMINI", true));

        assertEquals("GEMINI", selected.provider());
        assertEquals("GEMINI answer", selected.generate(prompt).content());
    }

    @Test
    void selectedUnconfiguredProviderFailsWithControlledError() {
        AiProvider selected = new SelectedAiProvider(AiProviderProperties.Provider.GEMINI,
                provider("OLLAMA", true), provider("GEMINI", false));

        AiProviderException error = assertThrows(
                AiProviderException.class, () -> selected.generate(prompt));

        assertEquals(AiProviderErrorCode.NOT_CONFIGURED, error.getCode());
        assertEquals("GEMINI", error.getProvider());
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
}
