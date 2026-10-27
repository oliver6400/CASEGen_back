package com.casegen.ai.provider.gemini;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.casegen.ai.provider.AiPrompt;
import com.casegen.ai.provider.AiPromptMessage;
import com.casegen.ai.provider.AiPromptRole;
import com.casegen.ai.provider.AiProviderErrorCode;
import com.casegen.ai.provider.AiProviderException;
import com.casegen.ai.provider.AiProviderProperties;
import com.casegen.ai.provider.AiProviderResult;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class GeminiAiProviderTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void reportsConfigurationProviderAndModel() {
        assertFalse(provider("", "http://localhost").configured());
        GeminiAiProvider provider = provider("key", "http://localhost");
        assertTrue(provider.configured());
        assertEquals("GEMINI", provider.provider());
        assertEquals("gemini-2.5-flash", provider.model());
    }

    @Test
    void mapsSystemAndHistoryAndReturnsFinalNonThoughtText() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> key = new AtomicReference<>();
        String response = """
                {"candidates":[{"content":{"parts":[
                  {"thought":true,"text":"internal reasoning"},
                  {"text":"Final response"}]},"finishReason":"STOP"}]}
                """;
        try (ServerFixture server = server(200, response, body, key)) {
            AiProviderResult result = provider("test-key", server.baseUrl()).generate(prompt());

            assertEquals("Final response", result.content());
            assertFalse(result.content().contains("internal reasoning"));
            assertEquals("GEMINI", result.provider());
            assertEquals("STOP", result.finishReason());
            assertEquals("test-key", key.get());
            assertTrue(body.get().contains("\"system_instruction\""));
            assertTrue(body.get().contains("CASEGen system"));
            assertTrue(body.get().contains("\"role\":\"user\""));
            assertTrue(body.get().contains("\"role\":\"model\""));
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
    void timeoutMapsToTimeout() throws Exception {
        HttpClient client = failingClient(new HttpTimeoutException("timeout"));
        GeminiAiProvider provider = new GeminiAiProvider(properties("key", "http://localhost"),
                client, json);

        assertCode(AiProviderErrorCode.TIMEOUT, () -> provider.generate(prompt()));
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
    void responseWithoutUsefulTextMapsToInvalidResponse() throws Exception {
        String response = "{\"candidates\":[{\"content\":{\"parts\":[{\"thought\":true,\"text\":\"hidden\"}]}}]}";
        try (ServerFixture server = server(200, response, new AtomicReference<>(),
                new AtomicReference<>())) {
            assertCode(AiProviderErrorCode.INVALID_RESPONSE,
                    () -> provider("key", server.baseUrl()).generate(prompt()));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private HttpClient failingClient(Exception exception) throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(), any(HttpResponse.BodyHandler.class))).thenThrow(exception);
        return client;
    }

    private GeminiAiProvider provider(String apiKey, String baseUrl) {
        return new GeminiAiProvider(properties(apiKey, baseUrl));
    }

    private AiProviderProperties.Gemini properties(String apiKey, String baseUrl) {
        return new AiProviderProperties.Gemini(
                apiKey, "gemini-2.5-flash", baseUrl, Duration.ofSeconds(2));
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
            AtomicReference<String> apiKey) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1beta/models/gemini-2.5-flash:generateContent", exchange -> {
            apiKey.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
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
        String baseUrl() { return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta"; }
        @Override public void close() { server.stop(0); }
    }
}
