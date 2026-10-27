package com.casegen.collaboration;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

@Component
public class WebSocketSubscriptionInterceptor implements ChannelInterceptor {
    private static final Pattern MODEL_TOPIC = Pattern.compile("^/topic/models/([0-9a-fA-F-]{36})$");
    private static final Pattern DIAGRAM_TOPIC = Pattern.compile(
            "^/topic/diagrams/([0-9a-fA-F-]{36})(?:/presence)?$");
    private static final Pattern PROJECT_AI_TOPIC = Pattern.compile(
            "^/topic/projects/([0-9a-fA-F-]{36})/ai$");

    private final JwtDecoder jwtDecoder;
    private final RealtimeSubscriptionAuthorizationService authorization;

    public WebSocketSubscriptionInterceptor(JwtDecoder jwtDecoder,
            RealtimeSubscriptionAuthorizationService authorization) {
        this.jwtDecoder = jwtDecoder;
        this.authorization = authorization;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) accessor = StompHeaderAccessor.wrap(message);
        if (accessor.getMessageType() == SimpMessageType.CONNECT) authenticate(accessor);
        if (accessor.getMessageType() == SimpMessageType.SUBSCRIBE) authorize(accessor);
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || header.isBlank()) return;
        if (!header.startsWith("Bearer ")) throw new AccessDeniedException("Invalid WebSocket authorization");
        Jwt jwt = jwtDecoder.decode(header.substring(7));
        UUID.fromString(jwt.getSubject());
        accessor.setUser(UsernamePasswordAuthenticationToken.authenticated(
                jwt.getSubject(), jwt.getTokenValue(), List.of()));
    }

    private void authorize(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        UUID userId = accessor.getUser() == null ? null : UUID.fromString(accessor.getUser().getName());
        Matcher model = MODEL_TOPIC.matcher(destination == null ? "" : destination);
        Matcher projectAi = PROJECT_AI_TOPIC.matcher(destination == null ? "" : destination);
        if (projectAi.matches()) {
            authorization.requireProjectAi(UUID.fromString(projectAi.group(1)), userId);
            return;
        }
        if (model.matches()) {
            authorization.requireModelRead(UUID.fromString(model.group(1)), userId);
            return;
        }
        Matcher diagram = DIAGRAM_TOPIC.matcher(destination == null ? "" : destination);
        if (diagram.matches()) {
            authorization.requireDiagramRead(UUID.fromString(diagram.group(1)), userId);
            return;
        }
        throw new AccessDeniedException("Subscription destination is not allowed");
    }
}
