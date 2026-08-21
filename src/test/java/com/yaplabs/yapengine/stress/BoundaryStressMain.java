package com.yaplabs.yapengine.stress;

import com.yaplabs.yapengine.core.spatial.SpatialQuadrant;
import com.yaplabs.yapengine.sequencing.SequenceToken;
import com.yaplabs.yapengine.sync.handoff.ChunkSyncLayer;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Headless boundary-crossing swarm — stand-in for Mineself / McProtocolLib bots.
 * Spawns N workers that submit rapid quad-tree handoffs against Threads 7–8.
 *
 * <pre>
 *   gradle boundaryStress -Dyap.stress.bots=100 -Dyap.stress.seconds=60
 * </pre>
 */
public final class BoundaryStressMain {

    public static void main(String[] args) throws InterruptedException {
        int bots = Integer.getInteger("yap.stress.bots", 100);
        int seconds = Integer.getInteger("yap.stress.seconds", 60);
        long maxHandoffs = Long.getLong("yap.stress.handoffs", 50_000L);

        System.out.println("BoundaryStress: bots=" + bots
                + " seconds=" + seconds
                + " maxHandoffs=" + maxHandoffs);

        ChunkSyncLayer layer = new ChunkSyncLayer();
        layer.start();

        LongAdder submitted = new LongAdder();
        LongAdder applied = new LongAdder();
        AtomicLong errors = new AtomicLong();
        long deadline = System.nanoTime() + seconds * 1_000_000_000L;

        Thread[] workers = new Thread[bots];
        for (int i = 0; i < bots; i++) {
            int botId = i;
            workers[i] = new Thread(() -> {
                long n = 0;
                String stream = "bot-" + botId;
                while (System.nanoTime() < deadline && submitted.sum() < maxHandoffs) {
                    String id = stream + "-" + n;
                    try {
                        layer.submitHandoff(new ChunkSyncLayer.Handoff(
                                id,
                                "inv:bot-" + (botId % Math.max(1, bots / 4)),
                                SpatialQuadrant.byId((int) (n & 3)),
                                SpatialQuadrant.byId((int) ((n + 1) & 3)),
                                SequenceToken.next(stream),
                                applied::increment
                        ));
                        submitted.increment();
                    } catch (RuntimeException ex) {
                        errors.incrementAndGet();
                    }
                    n++;
                }
            }, "stress-bot-" + i);
            workers[i].setDaemon(true);
            workers[i].start();
        }

        long start = System.currentTimeMillis();
        while (System.nanoTime() < deadline) {
            Thread.sleep(5_000);
            printStats(layer, submitted, applied, errors, start);
        }
        for (Thread t : workers) {
            t.join(5_000);
        }

        // Drain pipeline
        long waitUntil = System.nanoTime() + 30_000_000_000L;
        while (layer.pending() > 0 && System.nanoTime() < waitUntil) {
            Thread.sleep(50);
        }
        printStats(layer, submitted, applied, errors, start);

        layer.stop();
        long sub = submitted.sum();
        long app = applied.sum();
        long processed = layer.getProcessed();
        System.out.println("DONE submitted=" + sub + " applied=" + app
                + " processed=" + processed + " errors=" + errors.get()
                + " pending=" + layer.pending());

        if (errors.get() > 0 || processed < app * 0.95) {
            System.exit(1);
        }
    }

    private static void printStats(ChunkSyncLayer layer, LongAdder submitted,
                                   LongAdder applied, AtomicLong errors, long startMs) {
        long elapsed = Math.max(1, System.currentTimeMillis() - startMs);
        long sub = submitted.sum();
        System.out.printf(
                "t=%ds submitted=%d applied=%d processed=%d pending=%d errors=%d rate=%.0f/s%n",
                elapsed / 1000,
                sub,
                applied.sum(),
                layer.getProcessed(),
                layer.pending(),
                errors.get(),
                sub * 1000.0 / elapsed
        );
    }
}
