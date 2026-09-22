package com.casegen.collaboration;

import com.casegen.auth.domain.User;
import com.casegen.auth.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class DiagramPresenceService {
    private static final Duration TIMEOUT = Duration.ofSeconds(45);
    private final Map<UUID, Map<String, PresenceParticipant>> byDiagram = new ConcurrentHashMap<>();
    private final Map<String, UUID> diagramBySession = new ConcurrentHashMap<>();
    private final UserRepository users;
    private final RealtimeSubscriptionAuthorizationService authorization;
    private final SimpMessagingTemplate messaging;

    public DiagramPresenceService(UserRepository users,
            RealtimeSubscriptionAuthorizationService authorization, SimpMessagingTemplate messaging) {
        this.users = users;
        this.authorization = authorization;
        this.messaging = messaging;
    }

    public void update(UUID diagramId, UUID userId, String sessionId, PresenceCommand command) {
        authorization.requireDiagramRead(diagramId, userId);
        PresenceActivity activity = command.activity() == null ? PresenceActivity.VIEWING : command.activity();
        if (activity == PresenceActivity.EDITING) authorization.requireDiagramWrite(diagramId, userId);
        User user = users.findById(userId).filter(User::isEnabled)
                .orElseThrow(() -> new IllegalArgumentException("User is unavailable"));
        PresenceParticipant participant = new PresenceParticipant(userId, user.getDisplayName(), diagramId,
                command.selectedElementId(), activity, Instant.now());
        UUID previousDiagram = diagramBySession.put(sessionId, diagramId);
        if (previousDiagram != null && !previousDiagram.equals(diagramId)) remove(previousDiagram, sessionId);
        byDiagram.computeIfAbsent(diagramId, ignored -> new ConcurrentHashMap<>()).put(sessionId, participant);
        publish(diagramId);
    }

    public void disconnect(String sessionId) {
        UUID diagramId = diagramBySession.remove(sessionId);
        if (diagramId != null) {
            remove(diagramId, sessionId);
            publish(diagramId);
        }
    }

    @Scheduled(fixedDelay = 15_000)
    public void evictExpired() {
        Instant threshold = Instant.now().minus(TIMEOUT);
        byDiagram.forEach((diagramId, sessions) -> {
            boolean changed = sessions.entrySet().removeIf(entry -> {
                boolean expired = entry.getValue().lastSeen().isBefore(threshold);
                if (expired) diagramBySession.remove(entry.getKey(), diagramId);
                return expired;
            });
            if (changed) publish(diagramId);
        });
    }

    private void remove(UUID diagramId, String sessionId) {
        Map<String, PresenceParticipant> sessions = byDiagram.get(diagramId);
        if (sessions == null) return;
        sessions.remove(sessionId);
        if (sessions.isEmpty()) byDiagram.remove(diagramId, sessions);
    }

    private void publish(UUID diagramId) {
        Map<String, PresenceParticipant> sessions = byDiagram.getOrDefault(diagramId, Map.of());
        Map<UUID, PresenceParticipant> latestByUser = new ConcurrentHashMap<>();
        sessions.values().forEach(participant -> latestByUser.merge(participant.userId(), participant,
                (left, right) -> left.lastSeen().isAfter(right.lastSeen()) ? left : right));
        List<PresenceParticipant> participants = latestByUser.values().stream()
                .sorted(Comparator.comparing(PresenceParticipant::displayName)
                        .thenComparing(PresenceParticipant::userId))
                .toList();
        messaging.convertAndSend("/topic/diagrams/" + diagramId + "/presence",
                new PresenceSnapshot(diagramId, participants, Instant.now()));
    }
}
