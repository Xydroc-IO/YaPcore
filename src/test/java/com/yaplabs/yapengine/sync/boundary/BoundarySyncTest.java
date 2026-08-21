package com.yaplabs.yapengine.sync.boundary;

import com.yaplabs.yapengine.core.spatial.SpatialQuadrant;
import com.yaplabs.yapengine.sequencing.SequenceToken;
import com.yaplabs.yapengine.sync.handoff.ChunkSyncLayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Non-Fray boundary handoff tests (fast CI path).
 */
class BoundarySyncTest {

    private ChunkSyncLayer layer;

    @BeforeEach
    void setUp() {
        layer = new ChunkSyncLayer();
        layer.start();
    }

    @AfterEach
    void tearDown() {
        if (layer != null) {
            layer.stop();
        }
    }

    @Test
    @Timeout(30)
    void singleEntityHandoffCompletes() throws InterruptedException {
        CountDownLatch applied = new CountDownLatch(1);
        layer.submitHandoff(new ChunkSyncLayer.Handoff(
                "e1",
                "inv:e1",
                SpatialQuadrant.NW,
                SpatialQuadrant.NE,
                SequenceToken.next("e1"),
                applied::countDown
        ));
        assertTrue(applied.await(5, TimeUnit.SECONDS));

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (layer.getProcessed() < 1 && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertTrue(layer.getProcessed() >= 1,
                "expected >= 1 processed=" + layer.getProcessed());
    }

    /**
     * Regression: T8 used to park on inbound.poll(20ms) while the lease grant
     * sat on another queue — ~20ms × N serial border barriers ≈ high-pop MSPT blowup.
     * 40 sequential handoffs must finish well under the old 40×20ms floor.
     */
    @Test
    @Timeout(30)
    void sequentialHandoffsAreNotPollGapped() throws InterruptedException {
        final int n = 40;
        CountDownLatch applied = new CountDownLatch(n);
        long start = System.nanoTime();
        for (int i = 0; i < n; i++) {
            final int id = i;
            layer.submitHandoff(new ChunkSyncLayer.Handoff(
                    "lat-" + id,
                    "inv:lat-" + id,
                    SpatialQuadrant.NW,
                    SpatialQuadrant.SE,
                    SequenceToken.next("lat-" + id),
                    applied::countDown
            ));
        }
        assertTrue(applied.await(5, TimeUnit.SECONDS), "handoffs did not complete");
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        // Old bug floor ≈ 800ms; healthy path should be tens of ms even on a loaded CI box.
        assertTrue(elapsedMs < 400,
                "sequential handoffs took " + elapsedMs + "ms (poll-gap regression?)");
        assertTrue(layer.getProcessed() >= n,
                "processed=" + layer.getProcessed());
    }

    @Test
    @Tag("soak")
    @Timeout(120)
    void soakRapidBoundaryCrossings() throws InterruptedException {
        int bots = Integer.getInteger("yap.soak.bots", 32);
        int seconds = Integer.getInteger("yap.soak.seconds", 30);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        AtomicInteger submitted = new AtomicInteger();
        AtomicInteger applied = new AtomicInteger();

        Thread[] workers = new Thread[Math.min(bots, 32)];
        for (int w = 0; w < workers.length; w++) {
            int workerId = w;
            workers[w] = new Thread(() -> {
                int n = 0;
                while (System.nanoTime() < deadline) {
                    String id = "bot-" + workerId + "-" + n;
                    layer.submitHandoff(new ChunkSyncLayer.Handoff(
                            id,
                            "inv:bot-" + workerId,
                            SpatialQuadrant.byId(n & 3),
                            SpatialQuadrant.byId((n + 1) & 3),
                            SequenceToken.next(id),
                            applied::incrementAndGet
                    ));
                    submitted.incrementAndGet();
                    n++;
                }
            }, "soak-bot-" + w);
            workers[w].start();
        }
        for (Thread t : workers) {
            t.join();
        }

        long target = applied.get();
        long waitUntil = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (layer.getProcessed() < target && System.nanoTime() < waitUntil) {
            Thread.sleep(10);
        }
        assertTrue(layer.getProcessed() >= target * 0.99,
                "processed=" + layer.getProcessed() + " applied=" + target
                        + " submitted=" + submitted.get());
    }
}
