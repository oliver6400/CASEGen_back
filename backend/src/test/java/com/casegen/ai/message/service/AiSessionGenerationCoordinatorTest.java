package com.casegen.ai.message.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.casegen.ai.provider.AiProviderErrorCode;
import com.casegen.ai.provider.AiProviderException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AiSessionGenerationCoordinatorTest {

    @Test
    void sameSessionRunsOneGenerationAtATimeInWaitingOrder() throws Exception {
        AiSessionGenerationCoordinator coordinator = new AiSessionGenerationCoordinator();
        UUID sessionId = UUID.randomUUID();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> coordinator.execute(sessionId, () -> {
                enter(active, maximum);
                order.add("first-start");
                firstEntered.countDown();
                await(releaseFirst);
                order.add("first-end");
                active.decrementAndGet();
                return "first";
            }));
            assertTrue(firstEntered.await(2, TimeUnit.SECONDS));
            var second = executor.submit(() -> coordinator.execute(sessionId, () -> {
                enter(active, maximum);
                order.add("second-start");
                active.decrementAndGet();
                return "second";
            }));

            Thread.sleep(100);
            assertFalse(second.isDone());
            assertEquals(1, maximum.get());
            releaseFirst.countDown();
            assertEquals("first", first.get(2, TimeUnit.SECONDS));
            assertEquals("second", second.get(2, TimeUnit.SECONDS));
        }

        assertEquals(List.of("first-start", "first-end", "second-start"), order);
        assertEquals(0, coordinator.trackedSessionCount());
    }

    @Test
    void differentSessionsCanGenerateInParallel() throws Exception {
        AiSessionGenerationCoordinator coordinator = new AiSessionGenerationCoordinator();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        CountDownLatch bothEntered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> coordinator.execute(UUID.randomUUID(),
                    () -> concurrentTurn(active, maximum, bothEntered, release)));
            var second = executor.submit(() -> coordinator.execute(UUID.randomUUID(),
                    () -> concurrentTurn(active, maximum, bothEntered, release)));
            assertTrue(bothEntered.await(2, TimeUnit.SECONDS));
            assertEquals(2, maximum.get());
            release.countDown();
            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
        }
        assertEquals(0, coordinator.trackedSessionCount());
    }

    @Test
    void providerErrorReleasesSessionAndNextTurnContinues() {
        AiSessionGenerationCoordinator coordinator = new AiSessionGenerationCoordinator();
        UUID sessionId = UUID.randomUUID();

        assertThrows(IllegalStateException.class,
                () -> coordinator.execute(sessionId, () -> { throw new IllegalStateException("failed"); }));
        assertEquals(0, coordinator.trackedSessionCount());
        assertEquals("next", coordinator.execute(sessionId, () -> "next"));
        assertEquals(0, coordinator.trackedSessionCount());
    }

    @Test
    void timeoutReleasesSessionAndNextTurnContinues() {
        AiSessionGenerationCoordinator coordinator = new AiSessionGenerationCoordinator();
        UUID sessionId = UUID.randomUUID();

        assertThrows(AiProviderException.class, () -> coordinator.execute(sessionId,
                () -> { throw new AiProviderException(AiProviderErrorCode.TIMEOUT,
                        "OLLAMA", "timeout"); }));
        assertEquals("next", coordinator.execute(sessionId, () -> "next"));
        assertEquals(0, coordinator.trackedSessionCount());
    }

    private String concurrentTurn(AtomicInteger active, AtomicInteger maximum,
            CountDownLatch entered, CountDownLatch release) {
        enter(active, maximum);
        entered.countDown();
        await(release);
        active.decrementAndGet();
        return "done";
    }

    private void enter(AtomicInteger active, AtomicInteger maximum) {
        int current = active.incrementAndGet();
        maximum.accumulateAndGet(current, Math::max);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) throw new IllegalStateException("test timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
