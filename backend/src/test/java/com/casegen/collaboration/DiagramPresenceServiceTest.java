package com.casegen.collaboration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.casegen.auth.domain.User;
import com.casegen.auth.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class DiagramPresenceServiceTest {
    private final UserRepository users = Mockito.mock(UserRepository.class);
    private final RealtimeSubscriptionAuthorizationService authorization =
            Mockito.mock(RealtimeSubscriptionAuthorizationService.class);
    private final SimpMessagingTemplate messaging = Mockito.mock(SimpMessagingTemplate.class);
    private final DiagramPresenceService service = new DiagramPresenceService(users, authorization, messaging);

    @Test
    void publishesSelectionAndRemovesItOnDisconnect() {
        UUID diagramId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID elementId = UUID.randomUUID();
        User user = Mockito.mock(User.class);
        when(user.isEnabled()).thenReturn(true);
        when(user.getDisplayName()).thenReturn("Pablo");
        when(users.findById(userId)).thenReturn(Optional.of(user));

        service.update(diagramId, userId, "session-1",
                new PresenceCommand(elementId, PresenceActivity.SELECTING));

        verify(authorization).requireDiagramRead(diagramId, userId);
        verify(authorization, never()).requireDiagramWrite(diagramId, userId);
        ArgumentCaptor<PresenceSnapshot> snapshots = ArgumentCaptor.forClass(PresenceSnapshot.class);
        verify(messaging).convertAndSend(Mockito.eq("/topic/diagrams/" + diagramId + "/presence"), snapshots.capture());
        assertEquals("Pablo", snapshots.getValue().participants().getFirst().displayName());
        assertEquals(elementId, snapshots.getValue().participants().getFirst().selectedElementId());

        service.disconnect("session-1");
        verify(messaging, Mockito.times(2)).convertAndSend(Mockito.eq("/topic/diagrams/" + diagramId + "/presence"), snapshots.capture());
        assertTrue(snapshots.getAllValues().getLast().participants().isEmpty());
    }

    @Test
    void editingRequiresProjectWritePermission() {
        UUID diagramId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = Mockito.mock(User.class);
        when(user.isEnabled()).thenReturn(true);
        when(user.getDisplayName()).thenReturn("Oliver");
        when(users.findById(userId)).thenReturn(Optional.of(user));

        service.update(diagramId, userId, "session-2",
                new PresenceCommand(UUID.randomUUID(), PresenceActivity.EDITING));

        verify(authorization).requireDiagramWrite(diagramId, userId);
    }
}
