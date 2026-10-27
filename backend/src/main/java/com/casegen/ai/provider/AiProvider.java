package com.casegen.ai.provider;

public interface AiProvider {

    AiProviderResult generate(AiPrompt prompt);

    String provider();

    String model();

    boolean configured();
}
