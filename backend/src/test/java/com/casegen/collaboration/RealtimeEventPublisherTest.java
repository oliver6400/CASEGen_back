package com.casegen.collaboration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class RealtimeEventPublisherTest {

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsSmallSemanticEnvelopeWithActorAndRevision() {
        ApplicationEventPublisher applicationEvents = Mockito.mock(ApplicationEventPublisher.class);
        RealtimeEventPublisher publisher = new RealtimeEventPublisher(applicationEvents);
        UUID actor = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        UUID model = UUID.randomUUID();
        UUID entity = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(actor.toString(), "", List.of()));

        publisher.semantic(RealtimeEventType.PROPERTY_UPDATED, project, model, entity, 12);

        ArgumentCaptor<RealtimeEvent> event = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(applicationEvents).publishEvent(event.capture());
        assertEquals(RealtimeEventType.PROPERTY_UPDATED, event.getValue().eventType());
        assertEquals(project, event.getValue().projectId());
        assertEquals(model, event.getValue().modelId());
        assertEquals(entity, event.getValue().entityId());
        assertEquals(12L, event.getValue().modelRevision());
        assertNull(event.getValue().diagramRevision());
        assertEquals(actor, event.getValue().actorUserId());
    }
}
