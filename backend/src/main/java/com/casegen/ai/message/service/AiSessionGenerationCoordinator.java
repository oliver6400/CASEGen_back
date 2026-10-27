package com.casegen.ai.message.service;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class AiSessionGenerationCoordinator {
    private final ConcurrentHashMap<UUID, SessionGate> sessions = new ConcurrentHashMap<>();

    public <T> T execute(UUID sessionId, Supplier<T> generation) {
        Objects.requireNonNull(sessionId, "sessionId is required");
        Objects.requireNonNull(generation, "generation is required");
        SessionGate gate = sessions.compute(sessionId, (key, current) -> {
            SessionGate selected = current == null ? new SessionGate() : current;
            selected.retain();
            return selected;
        });
        gate.lock();
        try {
            return generation.get();
        } finally {
            gate.unlock();
            sessions.computeIfPresent(sessionId, (key, current) ->
                    current == gate && gate.release() == 0 ? null : current);
        }
    }

    int trackedSessionCount() {
        return sessions.size();
    }

    private static final class SessionGate {
        private final ReentrantLock lock = new ReentrantLock(true);
        private int references;

        private void retain() { references++; }
        private int release() { return --references; }
        private void lock() { lock.lock(); }
        private void unlock() { lock.unlock(); }
    }
}
