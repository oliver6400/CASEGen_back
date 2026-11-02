package com.casegen.generation.frontend;

import static com.casegen.generation.logical.dto.LogicalModelDtos.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.casegen.generation.frontend.service.ReactNativeFrontendRenderer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class ReactNativeFrontendRendererTest {
    @Test
    void rendersLoginAndCrudClient(@TempDir Path workspace) throws Exception {
        var json = JsonMapper.builder().build();
        var profile = json.valueToTree(java.util.Map.of("profile", java.util.Map.of("artifactId", "ventas-app")));
        var table = new Table("customer", UUID.randomUUID().toString(), null, List.of(
                new Column("id", UUID.randomUUID().toString(), "UUID", false, 1),
                new Column("name", UUID.randomUUID().toString(), "VARCHAR(255)", false, 2),
                new Column("active", UUID.randomUUID().toString(), "BOOLEAN", true, 3)),
                new Key("pk_customer", List.of("id"), null), List.of(), List.of(), List.of(), List.of());
        var input = new Response(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), profile,
                "VALID", "checksum", UUID.randomUUID(), Instant.now(), List.of(table), List.of());

        new ReactNativeFrontendRenderer().render(workspace, input,
                new ReactNativeFrontendRenderer.Traceability("Ventas", 3));

        assertTrue(Files.exists(workspace.resolve("App.tsx")));
        assertTrue(Files.exists(workspace.resolve("src/screens/LoginScreen.tsx")));
        assertTrue(Files.exists(workspace.resolve("src/screens/HomeScreen.tsx")));
        assertTrue(Files.exists(workspace.resolve("src/screens/CustomerScreen.tsx")));
        assertTrue(Files.exists(workspace.resolve("src/components/VoiceAssistantModal.tsx")));
        assertTrue(Files.exists(workspace.resolve("src/db/sqlite.ts")));
        assertTrue(Files.exists(workspace.resolve("src/db/SyncQueueService.ts")));
        String pkg = Files.readString(workspace.resolve("package.json"));
        String login = Files.readString(workspace.resolve("src/screens/LoginScreen.tsx"));
        String home = Files.readString(workspace.resolve("src/screens/HomeScreen.tsx"));
        String auth = Files.readString(workspace.resolve("src/auth/AuthContext.tsx"));
        String client = Files.readString(workspace.resolve("src/api/client.ts"));
        String config = Files.readString(workspace.resolve("src/config.ts"));
        String customerScreen = Files.readString(workspace.resolve("src/screens/CustomerScreen.tsx"));
        String voiceModal = Files.readString(workspace.resolve("src/components/VoiceAssistantModal.tsx"));
        String sqlite = Files.readString(workspace.resolve("src/db/sqlite.ts"));
        String sync = Files.readString(workspace.resolve("src/db/SyncQueueService.ts"));
        assertTrue(pkg.contains("expo-sqlite"));
        assertTrue(sqlite.contains("offline_sync_queue"));
        assertTrue(sqlite.contains("customer"));
        assertTrue(sync.contains("syncPendingQueue"));
        assertTrue(home.contains("SyncQueueService"));
        assertTrue(login.contains("placeholder='Email'"));
        assertTrue(login.contains("Registrarme"));
        assertTrue(auth.contains("/api/auth/register"));
        assertTrue(auth.contains("JSON.stringify({ displayName, email, password })"));
        assertTrue(config.contains("10.0.2.2"));
        assertTrue(auth.contains("/api/auth/login"));
        assertTrue(auth.contains("JSON.stringify({ email, password })"));
        assertTrue(client.contains("Authorization: `Bearer ${accessToken}`"));
        assertTrue(customerScreen.contains("'/api/customer'"));
        assertTrue(customerScreen.contains("method: 'PUT'"));
        assertTrue(customerScreen.contains("method: 'DELETE'"));
        assertTrue(customerScreen.contains("SyncQueueService"));
        assertTrue(customerScreen.contains("getLocalItems"));
        assertTrue(voiceModal.contains("'/api/ai/voice-command'"));
        assertTrue(voiceModal.contains("SyncQueueService"));
    }
}
