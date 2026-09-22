package com.casegen.interoperability.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

class GeminiImageAnalysisProviderTest {
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void validJpegAndJsonReturnDetectedDiagram() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> apiKey = new AtomicReference<>();
        try (ServerFixture server = server(200, validGeminiResponse(), body, apiKey)) {
            GeminiImageAnalysisProvider provider = provider(server);

            DetectedClassDiagram result = provider.analyze(
                    new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff}, "image/jpeg", "uml.jpg");

            assertEquals("Cliente", result.detectedClassifiers().getFirst().name());
            assertEquals("test-gemini-key", apiKey.get());
            assertTrue(body.get().contains("\"inline_data\""));
            assertTrue(body.get().contains("\"mime_type\":\"image/jpeg\""));
            assertTrue(body.get().contains("\"responseMimeType\":\"application/json\""));
            assertTrue(body.get().contains("\"responseJsonSchema\""));
            assertTrue(body.get().contains("vertically contiguous compartments"));
            assertTrue(body.get().contains("branched generalization"));
            assertFalse(body.get().contains("test-gemini-key"));
        }
    }

    @Test
    void validPngUsesRealMimeType() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        try (ServerFixture server = server(200, validGeminiResponse(), body, new AtomicReference<>())) {
            provider(server).analyze(new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47}, "image/png", "uml.png");
            assertTrue(body.get().contains("\"mime_type\":\"image/png\""));
        }
    }

    @Test
    void invalidJsonIsRejectedBeforePreview() throws Exception {
        String response = json.writeValueAsString(Map.of("candidates", List.of(Map.of(
                "content", Map.of("parts", List.of(Map.of("text", "not-json")))))));
        try (ServerFixture server = server(200, response, new AtomicReference<>(), new AtomicReference<>())) {
            ImageImportException error = assertThrows(ImageImportException.class,
                    () -> provider(server).analyze(new byte[] {1}, "image/webp", "uml.webp"));
            assertEquals("GEMINI_INVALID_RESPONSE", error.getCode());
            assertEquals(HttpStatus.BAD_GATEWAY, error.getStatus());
        }
    }

    @Test
    void unauthorizedMapsToInvalidConfiguration() throws Exception {
        assertProviderError(401, "GEMINI_CONFIGURATION_INVALID", HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void forbiddenMapsToInvalidConfiguration() throws Exception {
        assertProviderError(403, "GEMINI_CONFIGURATION_INVALID", HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void badProviderRequestMapsToControlledClientError() throws Exception {
        assertProviderError(400, "GEMINI_INVALID_REQUEST", HttpStatus.BAD_REQUEST);
    }

    @Test
    void quotaMapsToTooManyRequests() throws Exception {
        assertProviderError(429, "GEMINI_QUOTA_EXCEEDED", HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void providerFailureMapsToTemporarilyUnavailable() throws Exception {
        assertProviderError(503, "GEMINI_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE);
    }

    private void assertProviderError(int status, String code, HttpStatus expectedStatus) throws Exception {
        String response = json.writeValueAsString(Map.of("error", Map.of(
                "status", "PROVIDER_CODE", "message", "safe provider message")));
        try (ServerFixture server = server(status, response, new AtomicReference<>(), new AtomicReference<>())) {
            ImageImportException error = assertThrows(ImageImportException.class,
                    () -> provider(server).analyze(new byte[] {1}, "image/jpeg", "uml.jpg"));
            assertEquals(code, error.getCode());
            assertEquals(expectedStatus, error.getStatus());
            assertFalse(error.getMessage().contains("test-gemini-key"));
        }
    }

    private GeminiImageAnalysisProvider provider(ServerFixture server) {
        return new GeminiImageAnalysisProvider("test-gemini-key", "gemini-2.5-flash",
                "http://127.0.0.1:" + server.port() + "/v1beta");
    }

    private ServerFixture server(int status, String response, AtomicReference<String> body,
            AtomicReference<String> apiKey) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1beta/models/gemini-2.5-flash:generateContent", exchange -> {
            apiKey.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return new ServerFixture(server);
    }

    private String validGeminiResponse() {
        String detected = """
                {"suggestedModelName":"Ventas","detectedClassifiers":[
                {"id":"c1","name":"Cliente","kind":"CLASS","visibility":"PUBLIC","confidence":0.99}],
                "detectedAttributes":[],"detectedOperations":[],"detectedRelationships":[],
                "warnings":[],"confidence":0.99}
                """.replace("\n", "");
        return json.writeValueAsString(Map.of("candidates", List.of(Map.of(
                "content", Map.of("parts", List.of(Map.of("text", detected)))))));
    }

    private record ServerFixture(HttpServer server) implements AutoCloseable {
        int port() { return server.getAddress().getPort(); }
        @Override public void close() { server.stop(0); }
    }
}
