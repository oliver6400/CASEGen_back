package com.casegen.collaboration;

import java.security.Principal;
import java.util.UUID;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
public class DiagramPresenceController {
    private final DiagramPresenceService presence;

    public DiagramPresenceController(DiagramPresenceService presence) {
        this.presence = presence;
    }

    @MessageMapping("/diagrams/{diagramId}/presence")
    public void update(@DestinationVariable UUID diagramId, PresenceCommand command,
            Principal principal, @Header("simpSessionId") String sessionId) {
        if (principal == null) throw new IllegalArgumentException("Authenticated presence is required");
        presence.update(diagramId, UUID.fromString(principal.getName()), sessionId, command);
    }
}
