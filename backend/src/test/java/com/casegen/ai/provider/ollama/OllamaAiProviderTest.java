package com.casegen.ai.provider.ollama;

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
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class OllamaAiProviderTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void reportsConfigurationProviderAndModel() {
        OllamaAiProvider provider = provider("http://localhost:11434", "qwen3:4b");

        assertTrue(provider.configured());
        assertEquals("OLLAMA", provider.provider());
        assertEquals("qwen3:4b", provider.model());
        assertFalse(provider("", "qwen3:4b").configured());
    }

    @Test
    void mapsPromptDisablesStreamingAndThinkingAndReturnsOnlyFinalContent() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        String response = """
                {"message":{"role":"assistant","content":"Final answer","thinking":"secret"},
                 "done":true,"done_reason":"stop"}
                """;
        try (ServerFixture server = server(200, response, requestBody)) {
            OllamaAiProvider provider = provider(server.baseUrl(), "qwen3:4b");

            AiProviderResult result = provider.generate(prompt());

            assertEquals("Final answer", result.content());
            assertEquals("OLLAMA", result.provider());
            assertEquals("qwen3:4b", result.model());
            assertEquals("stop", result.finishReason());
            assertFalse(result.content().contains("secret"));
            assertTrue(requestBody.get().contains("\"role\":\"system\""));
            assertTrue(requestBody.get().contains("\"content\":\"CASEGen system\""));
            assertTrue(requestBody.get().contains("\"role\":\"user\""));
            assertTrue(requestBody.get().contains("\"role\":\"assistant\""));
            assertTrue(requestBody.get().contains("\"stream\":false"));
            assertTrue(requestBody.get().contains("\"think\":false"));
        }
    }

    @Test
    void preservesNormalResponseWithoutThinkBlock() throws Exception {
        assertContent("Respuesta normal", "Respuesta normal");
    }

    @Test
    void removesLeadingThinkBlock() throws Exception {
        assertContent("""
                <think>
                internal reasoning in English
                </think>

                Respuesta final en español.
                """, "Respuesta final en español.");
    }

    @Test
    void removesReasoningBeforeOrphanClosingThinkTag() throws Exception {
        assertContent("""
                Okay, the user wants...
                Let me inspect the UML...
                </think>

                cliente, empleado, usuario
                """, "cliente, empleado, usuario");
    }

    @Test
    void preservesFinalResponseLineBreaksAfterOrphanClosingThinkTag() throws Exception {
        assertContent("""
                reasoning
                </think>


                respuesta con saltos
                """, "respuesta con saltos");
    }

    @Test
    void removesMultipleLeadingThinkBlocks() throws Exception {
        assertContent("""
                <think>first secret</think>
                <think>second secret</think>
                Respuesta final.
                """, "Respuesta final.");
    }

    @Test
    void keepsOnlyContentAfterLastOrphanClosingThinkTag() throws Exception {
        assertContent("""
                reasoning 1
                </think>
                reasoning 2
                </think>

                respuesta final
                """, "respuesta final");
    }

    @Test
    void preservesThinkWordInNormalText() throws Exception {
        assertContent("I think the relation Cliente-Pedido is correct.",
                "I think the relation Cliente-Pedido is correct.");
    }

    @Test
    void preservesUnrelatedMarkup() throws Exception {
        assertContent("Texto con <tag> normal pero no think.",
                "Texto con <tag> normal pero no think.");
    }

    @Test
    void responseContainingOnlyThinkBlockMapsToInvalidResponse() throws Exception {
        String response = response("<think>internal reasoning</think>");
        try (ServerFixture server = server(200, response, new AtomicReference<>())) {
            assertCode(AiProviderErrorCode.INVALID_RESPONSE,
                    () -> provider(server.baseUrl(), "qwen3:4b").generate(prompt()));
        }
    }

    @Test
    void orphanClosingThinkTagWithoutFinalContentMapsToInvalidResponse() throws Exception {
        String response = response("reasoning\n</think>\n\n");
        try (ServerFixture server = server(200, response, new AtomicReference<>())) {
            assertCode(AiProviderErrorCode.INVALID_RESPONSE,
                    () -> provider(server.baseUrl(), "qwen3:4b").generate(prompt()));
        }
    }

    @Test
    void ignoresSeparateThinkingFieldAndThinkTagsNeverReachResult() throws Exception {
        String response = """
                {"message":{"role":"assistant",
                 "content":"<think>hidden</think> Final segura",
                 "thinking":"separate hidden reasoning"},"done":true}
                """;
        try (ServerFixture server = server(200, response, new AtomicReference<>())) {
            AiProviderResult result = provider(server.baseUrl(), "qwen3:4b").generate(prompt());

            assertEquals("Final segura", result.content());
            assertFalse(result.content().contains("<think>"));
            assertFalse(result.content().contains("</think>"));
            assertFalse(result.content().contains("separate hidden reasoning"));
        }
    }

    @Test
    void unavailableServerMapsToUnavailable() throws Exception {
        HttpClient client = failingClient(new ConnectException("refused"));
        OllamaAiProvider provider = provider(client, Duration.ofSeconds(1));

        assertCode(AiProviderErrorCode.UNAVAILABLE, () -> provider.generate(prompt()));
    }

    @Test
    void timeoutMapsToTimeout() throws Exception {
        HttpClient client = failingClient(new HttpTimeoutException("timeout"));
        OllamaAiProvider provider = provider(client, Duration.ofMillis(10));

        assertCode(AiProviderErrorCode.TIMEOUT, () -> provider.generate(prompt()));
    }

    @Test
    void invalidJsonMapsToInvalidResponse() throws Exception {
        try (ServerFixture server = server(200, "not-json", new AtomicReference<>())) {
            assertCode(AiProviderErrorCode.INVALID_RESPONSE,
                    () -> provider(server.baseUrl(), "qwen3:4b").generate(prompt()));
        }
    }

    @Test
    void missingFinalContentMapsToInvalidResponse() throws Exception {
        try (ServerFixture server = server(200, "{\"message\":{}}", new AtomicReference<>())) {
            assertCode(AiProviderErrorCode.INVALID_RESPONSE,
                    () -> provider(server.baseUrl(), "qwen3:4b").generate(prompt()));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private HttpClient failingClient(Exception exception) throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(), any(HttpResponse.BodyHandler.class))).thenThrow(exception);
        return client;
    }

    private OllamaAiProvider provider(HttpClient client, Duration timeout) {
        return new OllamaAiProvider(new AiProviderProperties.Ollama(
                "http://localhost:11434", "qwen3:4b", timeout), client, json);
    }

    private OllamaAiProvider provider(String baseUrl, String model) {
        return new OllamaAiProvider(new AiProviderProperties.Ollama(
                baseUrl, model, Duration.ofSeconds(2)));
    }

    private AiPrompt prompt() {
        return new AiPrompt("CASEGen system", List.of(
                new AiPromptMessage(AiPromptRole.USER, "Question"),
                new AiPromptMessage(AiPromptRole.ASSISTANT, "Earlier answer"),
                new AiPromptMessage(AiPromptRole.USER, "Follow-up")));
    }

    private void assertCode(AiProviderErrorCode code, Runnable invocation) {
        AiProviderException error = assertThrows(AiProviderException.class, invocation::run);
        assertEquals(code, error.getCode());
        assertFalse(error.getMessage().contains("Question"));
    }

    private void assertContent(String source, String expected) throws Exception {
        try (ServerFixture server = server(200, response(source), new AtomicReference<>())) {
            String content = provider(server.baseUrl(), "qwen3:4b").generate(prompt()).content();
            assertEquals(expected, content);
            assertFalse(content.contains("<think>"));
            assertFalse(content.contains("</think>"));
        }
    }

    private String response(String content) throws Exception {
        return json.writeValueAsString(Map.of(
                "message", Map.of("role", "assistant", "content", content),
                "done", true));
    }

    private ServerFixture server(int status, String response, AtomicReference<String> body)
            throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", exchange -> {
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
        String baseUrl() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
        @Override public void close() { server.stop(0); }
    }
}
