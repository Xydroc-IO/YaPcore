package com.yaplabs.yapengine.sync.boundary;

import com.yaplabs.yapengine.core.spatial.SpatialQuadrant;
import com.yaplabs.yapengine.sequencing.SequenceToken;
import com.yaplabs.yapengine.sync.handoff.ChunkSyncLayer;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.pastalab.fray.junit.junit5.FrayTestExtension;
import org.pastalab.fray.junit.junit5.annotations.FrayTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fray-driven concurrent submit of boundary handoffs.
 * Completion is verified by {@link BoundarySyncTest} / soak harness — Fray
 * terminates the dedicated DLM/boundary worker threads, so we only assert that
 * concurrent submits do not throw or corrupt the inbound queues.
 */
@ExtendWith(FrayTestExtension.class)
class BoundarySyncFrayTest {

    @FrayTest(iterations = 200)
    @Timeout(120)
    void testEntityBoundaryHandoff() throws InterruptedException {
        ChunkSyncLayer layer = new ChunkSyncLayer();
        // Do not start dedicated T7/T8 loops under Fray — they use timed polls
        // and get TargetTerminateException. Exercise concurrent submit only.
        int entities = 16;
        AtomicInteger submitted = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(entities);

        Thread[] producers = new Thread[entities];
        for (int i = 0; i < entities; i++) {
            int idx = i;
            producers[i] = new Thread(() -> {
                try {
                    start.await();
                    String id = "entity-" + idx;
                    String invKey = (idx % 2 == 0) ? "inv:shared-chest" : "inv:" + id;
                    layer.submitHandoff(new ChunkSyncLayer.Handoff(
                            id,
                            invKey,
                            SpatialQuadrant.byId(idx & 3),
                            SpatialQuadrant.byId((idx + 1) & 3),
                            SequenceToken.next(id),
                            submitted::incrementAndGet
                    ));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }, "handoff-producer-" + i);
            producers[i].start();
        }

        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        for (Thread t : producers) {
            t.join();
        }
        assertTrue(layer.pending() >= entities,
                "all handoffs should be queued for T7/T8, pending=" + layer.pending());
    }
}
