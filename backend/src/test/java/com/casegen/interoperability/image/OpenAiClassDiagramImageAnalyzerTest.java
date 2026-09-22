package com.casegen.interoperability.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OpenAiClassDiagramImageAnalyzerTest {

    @Test
    void missingKeyReturnsFriendlyControlledError() {
        var analyzer = new OpenAiClassDiagramImageAnalyzer("", "", "");

        ImageImportException error = assertThrows(ImageImportException.class,
                () -> analyzer.analyze(new byte[] {1}, "image/png", "diagram.png"));

        assertFalse(analyzer.configured());
        assertEquals("IMAGE_ANALYSIS_NOT_CONFIGURED", error.getCode());
        assertEquals("El servicio de análisis de imágenes no está configurado.", error.getMessage());
    }

    @Test
    void configuredProviderSendsImageAndReturnsDetectedClassDiagram() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/responses", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String detected = """
                    {"suggestedModelName":"Ventas","detectedClassifiers":[
                    {"id":"c1","name":"Cliente","kind":"CLASS","visibility":"PUBLIC","confidence":0.99}],
                    "detectedAttributes":[],"detectedOperations":[],"detectedRelationships":[],
                    "warnings":[],"confidence":0.99}
                    """.replace("\n", "");
            String escaped = detected.replace("\\", "\\\\").replace("\"", "\\\"");
            byte[] response = ("{\"output\":[{\"content\":[{\"type\":\"output_text\",\"text\":\""
                    + escaped + "\"}]}]}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var analyzer = new OpenAiClassDiagramImageAnalyzer("backend-secret", "gpt-4o-mini",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/responses");

            DetectedClassDiagram result = analyzer.analyze(
                    new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff}, "image/jpeg", "diagram.jpg");

            assertTrue(analyzer.configured());
            assertEquals("OPENAI", analyzer.provider());
            assertEquals("Cliente", result.detectedClassifiers().getFirst().name());
            assertEquals("Bearer backend-secret", authorization.get());
            assertTrue(requestBody.get().contains("data:image/jpeg;base64,"));
            assertTrue(requestBody.get().contains("gpt-4o-mini"));
        } finally {
            server.stop(0);
        }
    }
}
