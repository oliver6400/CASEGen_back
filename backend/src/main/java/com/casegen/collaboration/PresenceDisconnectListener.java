package com.casegen.collaboration;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class PresenceDisconnectListener {
    private final DiagramPresenceService presence;

    public PresenceDisconnectListener(DiagramPresenceService presence) {
        this.presence = presence;
    }

    @EventListener
    public void disconnected(SessionDisconnectEvent event) {
        presence.disconnect(event.getSessionId());
    }
}
