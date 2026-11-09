package com.casegen.ai.provider.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.casegen.ai.provider.AiPrompt;
import com.casegen.ai.provider.AiPromptMessage;
import com.casegen.ai.provider.AiPromptRole;
import com.casegen.ai.provider.AiProviderErrorCode;
import com.casegen.ai.provider.AiProviderException;
import com.casegen.ai.provider.AiProviderProperties;
import com.casegen.ai.provider.AiProviderResult;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OpenAiAiProviderTest {

    @Test
    void reportsConfigurationProviderAndModel() {
        assertFalse(provider("", "http://localhost/v1").configured());
        OpenAiAiProvider provider = provider("key", "http://localhost/v1");
        assertTrue(provider.configured());
        assertEquals("OPENAI", provider.provider());
        assertEquals("gpt-6-luna", provider.model());
    }

    @Test
    void mapsInstructionsAndHistoryAndReturnsOutputText() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        String response = """
                {"status":"completed","output":[{"type":"message","content":[
                  {"type":"output_text","text":"Final response"}]}]}
                """;
        try (ServerFixture server = server(200, response, body, authorization)) {
            AiProviderResult result = provider("test-key", server.baseUrl()).generate(prompt());

            assertEquals("Final response", result.content());
            assertEquals("OPENAI", result.provider());
            assertEquals("completed", result.finishReason());
            assertEquals("Bearer test-key", authorization.get());
            assertTrue(body.get().contains("\"instructions\":\"CASEGen system\""));
            assertTrue(body.get().contains("\"model\":\"gpt-6-luna\""));
            assertTrue(body.get().contains("\"role\":\"user\""));
            assertTrue(body.get().contains("\"role\":\"assistant\""));
            assertTrue(body.get().contains("\"effort\":\"medium\""));
            assertFalse(body.get().contains("test-key"));
        }
    }

    @Test
    void rateLimitMapsToRateLimited() throws Exception {
        try (ServerFixture server = server(429, "{}", new AtomicReference<>(),
                new AtomicReference<>())) {
            assertCode(AiProviderErrorCode.RATE_LIMITED,
                    () -> provider("key", server.baseUrl()).generate(prompt()));
        }
    }

    @Test
    void invalidJsonMapsToInvalidResponse() throws Exception {
        try (ServerFixture server = server(200, "not-json", new AtomicReference<>(),
                new AtomicReference<>())) {
            assertCode(AiProviderErrorCode.INVALID_RESPONSE,
                    () -> provider("key", server.baseUrl()).generate(prompt()));
        }
    }

    @Test
    void responseWithoutOutputTextMapsToInvalidResponse() throws Exception {
        try (ServerFixture server = server(200, "{\"status\":\"completed\",\"output\":[]}",
                new AtomicReference<>(), new AtomicReference<>())) {
            assertCode(AiProviderErrorCode.INVALID_RESPONSE,
                    () -> provider("key", server.baseUrl()).generate(prompt()));
        }
    }

    private OpenAiAiProvider provider(String apiKey, String baseUrl) {
        return new OpenAiAiProvider(new AiProviderProperties.OpenAi(apiKey, "gpt-6-luna",
                baseUrl, Duration.ofSeconds(2), "medium"));
    }

    private AiPrompt prompt() {
        return new AiPrompt("CASEGen system", List.of(
                new AiPromptMessage(AiPromptRole.USER, "Question"),
                new AiPromptMessage(AiPromptRole.ASSISTANT, "Earlier answer")));
    }

    private void assertCode(AiProviderErrorCode code, Runnable invocation) {
        AiProviderException error = assertThrows(AiProviderException.class, invocation::run);
        assertEquals(code, error.getCode());
        assertFalse(error.getMessage().contains("Question"));
    }

    private ServerFixture server(int status, String response, AtomicReference<String> body,
            AtomicReference<String> authorization) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/responses", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return new ServerFixture(server);
    }

    private record ServerFixture(HttpServer server) implements AutoCloseable {
        String baseUrl() { return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1"; }
        @Override public void close() { server.stop(0); }
    }
}
