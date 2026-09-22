package com.casegen.collaboration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class WebSocketSubscriptionInterceptorTest {
    private final JwtDecoder decoder = Mockito.mock(JwtDecoder.class);
    private final RealtimeSubscriptionAuthorizationService authorization =
            Mockito.mock(RealtimeSubscriptionAuthorizationService.class);
    private final WebSocketSubscriptionInterceptor interceptor =
            new WebSocketSubscriptionInterceptor(decoder, authorization);

    @Test
    void authenticatesConnectWithTheWebAccessToken() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "HS256"), Map.of("sub", userId.toString()));
        when(decoder.decode("token")).thenReturn(jwt);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer token");

        Message<?> result = interceptor.preSend(message(accessor), Mockito.mock(org.springframework.messaging.MessageChannel.class));

        assertEquals(userId.toString(), MessageHeaderAccessor
                .getAccessor(result, StompHeaderAccessor.class).getUser().getName());
    }

    @Test
    void authorizesModelAndDiagramSubscriptionsIndependently() {
        UUID userId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        UUID diagramId = UUID.randomUUID();
        subscribe("/topic/models/" + modelId, userId);
        subscribe("/topic/diagrams/" + diagramId, userId);
        subscribe("/topic/diagrams/" + diagramId + "/presence", userId);

        verify(authorization).requireModelRead(modelId, userId);
        verify(authorization, Mockito.times(2)).requireDiagramRead(diagramId, userId);
    }

    @Test
    void rejectsAnyUnscopedTopic() {
        assertThrows(AccessDeniedException.class,
                () -> subscribe("/topic/models", UUID.randomUUID()));
    }

    private void subscribe(String destination, UUID userId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(UsernamePasswordAuthenticationToken.authenticated(
                userId.toString(), "", List.of()));
        interceptor.preSend(message(accessor), Mockito.mock(org.springframework.messaging.MessageChannel.class));
    }

    private Message<byte[]> message(StompHeaderAccessor accessor) {
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
