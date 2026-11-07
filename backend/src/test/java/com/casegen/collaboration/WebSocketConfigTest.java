package com.casegen.collaboration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.casegen.auth.config.CorsProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

class WebSocketConfigTest {

    @Test
    void appliesConfiguredOriginPatternsToTheStompEndpoint() {
        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        StompWebSocketEndpointRegistration endpoint =
                mock(StompWebSocketEndpointRegistration.class);
        when(registry.addEndpoint("/ws")).thenReturn(endpoint);

        WebSocketConfig config = new WebSocketConfig(
                mock(WebSocketSubscriptionInterceptor.class),
                new CorsProperties(
                        List.of("http://localhost:5173"),
                        List.of("http://192.168.*:[*]")));

        config.registerStompEndpoints(registry);

        verify(endpoint).setAllowedOriginPatterns("http://192.168.*:[*]");
    }
}
