package com.yapcore.paper.phase3;

import com.yaplabs.yapengine.core.spatial.ParallelGameCore;
import com.yaplabs.yapengine.core.spatial.SpatialSpawnRegion;
import com.yaplabs.yapengine.sync.handoff.ChunkSyncLayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class YapSpatialTickCoordinatorTest {

    private ChunkSyncLayer sync;
    private ParallelGameCore core;
    private YapSpatialTickCoordinator coord;

    @BeforeEach
    void setUp() {
        SpatialSpawnRegion.configure(false, 8);
        sync = new ChunkSyncLayer();
        sync.start();
        core = new ParallelGameCore(sync,
                new com.yaplabs.yapengine.bridge.CompatibilityBridge());
        core.start();
        coord = new YapSpatialTickCoordinator(core, sync);
        coord.start();
    }

    @AfterEach
    void tearDown() {
        coord.stop();
        core.stop();
        sync.stop();
        SpatialSpawnRegion.configure(false, 8);
    }

    @Test
    void borderDetectionMatchesQuadrantEdges() {
        assertTrue(YapSpatialTickCoordinator.isBorderChunk(0, 0)); // origin — all quads meet
        assertTrue(YapSpatialTickCoordinator.isBorderChunk(-1, 0));
        assertTrue(YapSpatialTickCoordinator.isBorderChunk(0, 5)); // east plane
        assertTrue(YapSpatialTickCoordinator.isBorderChunk(5, -1)); // south/north plane
        assertFalse(YapSpatialTickCoordinator.isBorderChunk(5, 5)); // deep SE interior
        assertFalse(YapSpatialTickCoordinator.isBorderChunk(-5, -5)); // deep NW
        assertFalse(YapSpatialTickCoordinator.isBorderChunk(1, 5));
    }

    @Test
    void spawnBoxDeepInteriorNotBorder_perimeterIs() {
        SpatialSpawnRegion.configure(true, 3);
        // Deep spawn interior — all neighbors SPAWN
        assertFalse(YapSpatialTickCoordinator.isBorderChunk(0, 0));
        assertFalse(YapSpatialTickCoordinator.isBorderChunk(1, 1));
        // Perimeter of box — neighbors leave SPAWN
        assertTrue(YapSpatialTickCoordinator.isBorderChunk(3, 0));
        assertTrue(YapSpatialTickCoordinator.isBorderChunk(0, -3));
        // Outside spawn, deep SE still interior
        assertFalse(YapSpatialTickCoordinator.isBorderChunk(8, 8));
    }

    @Test
    void singleQuadParallelTickRunsOffCaller() throws Exception {
        AtomicInteger ran = new AtomicInteger();
        String[] threadName = {""};
        String caller = Thread.currentThread().getName();
        assertTrue(coord.runParallelTick(
                () -> {
                    threadName[0] = Thread.currentThread().getName();
                    ran.incrementAndGet();
                },
                null, null, null));
        assertEquals(1, ran.get());
        assertTrue(threadName[0].startsWith("yap-t"),
                "expected spatial core, got " + threadName[0]);
        assertFalse(threadName[0].equals(caller), "must not run inline on caller");
    }

    @Test
    void runLeasedMutualExclusionOnSameKey() throws Exception {
        AtomicInteger held = new AtomicInteger();
        AtomicInteger max = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(2);
        CountDownLatch done = new CountDownLatch(2);
        Runnable work = () -> {
            start.countDown();
            try {
                start.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            boolean ok = coord.runLeased("c:world:1:1", () -> {
                int h = held.incrementAndGet();
                max.accumulateAndGet(h, Math::max);
                try {
                    Thread.sleep(30);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                held.decrementAndGet();
            });
            if (!ok) {
                // deny is acceptable for the loser
            }
            done.countDown();
        };
        Thread a = new Thread(work, "yap-t3-spatial-nw");
        Thread b = new Thread(work, "yap-t4-spatial-ne");
        a.start();
        b.start();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertTrue(max.get() <= 1, "lease must be mutually exclusive");
        assertTrue(coord.leasedMutationCount() + coord.leaseDenyCount() >= 1);
    }

    @Test
    void runBorderTickSyncRunsOnBoundaryUnderLease() throws Exception {
        AtomicInteger ran = new AtomicInteger();
        String[] threadName = {""};
        assertTrue(coord.runBorderTickSync("border:test", () -> {
            threadName[0] = Thread.currentThread().getName();
            ran.incrementAndGet();
        }));
        assertEquals(1, ran.get());
        assertTrue(threadName[0].startsWith("yap-t8-boundary"),
                "expected T8, got " + threadName[0]);
        assertTrue(coord.borderHandoffCount() >= 1);
        assertTrue(coord.leasedMutationCount() >= 1);
    }

    @Test
    void chunkKeyFormat() {
        assertEquals("c:world:3:-2", YapSpatialTickCoordinator.chunkKey("world", 3, -2));
    }
}
