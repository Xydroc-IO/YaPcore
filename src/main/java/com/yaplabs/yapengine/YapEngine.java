package com.yaplabs.yapengine;

import com.yaplabs.yapengine.bridge.CompatibilityBridge;
import com.yaplabs.yapengine.controller.EngineController;
import com.yaplabs.yapengine.core.spatial.BitwiseQuadrantIndex;
import com.yaplabs.yapengine.core.spatial.ParallelGameCore;
import com.yaplabs.yapengine.core.spatial.SpatialQuadrant;
import com.yaplabs.yapengine.network.traffic.TrafficCop;
import com.yaplabs.yapengine.sandbox.PluginSandbox;
import com.yaplabs.yapengine.sync.handoff.ChunkSyncLayer;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * YapEngine — slim server chassis (YapLabs architecture v2.0).
 * <p>
 * Product path: Folia owns game tick; this process owns edge, bridge, and I/O sandboxes.
 */
public final class YapEngine {

    private static final Logger LOG = Logger.getLogger("YapEngine");

    private final CompatibilityBridge bridge = new CompatibilityBridge();
    private final ChunkSyncLayer syncLayer = new ChunkSyncLayer();
    private final ParallelGameCore gameCore = new ParallelGameCore(syncLayer, bridge);
    private final PluginSandbox sandbox = new PluginSandbox(bridge, gameCore);
    private final TrafficCop trafficCop = new TrafficCop(gameCore, sandbox);
    private final EngineController controller = new EngineController(
            gameCore,
            trafficCop,
            syncLayer.dlm(),
            syncLayer.boundary(),
            sandbox.telemetry()
    );
    private final AtomicBoolean running = new AtomicBoolean(false);

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        LOG.info("=== YapEngine chassis boot (v2.0 — Folia owns game tick) ===");
        logAssignments();
        syncLayer.start();
        gameCore.start();
        bridge.start();
        trafficCop.start();
        controller.start();
        LOG.info("YapEngine chassis online — 16 logical channels (edge/I/O; not world tick)");
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        LOG.info("=== YapEngine shutdown ===");
        controller.stop();
        trafficCop.stop();
        bridge.stop();
        gameCore.stop();
        syncLayer.stop();
        sandbox.shutdown();
        LOG.info("YapEngine stopped");
    }

    public boolean isRunning() {
        return running.get();
    }

    public ParallelGameCore gameCore() {
        return gameCore;
    }

    public CompatibilityBridge bridge() {
        return bridge;
    }

    public ChunkSyncLayer syncLayer() {
        return syncLayer;
    }

    public PluginSandbox sandbox() {
        return sandbox;
    }

    public TrafficCop trafficCop() {
        return trafficCop;
    }

    private void logAssignments() {
        LOG.info("Core assignment map (v2.0 — chassis only; Folia = game tick):");
        LOG.info("  Thread 1  → Controller / Watchdog");
        LOG.info("  Thread 2  → Traffic Cop + SequenceToken (Epoll/Zstd)");
        LOG.info("  Thread 3  → Chassis worker quad 0 (NW) — legacy Phase 3 on Paper benches");
        LOG.info("  Thread 4  → Chassis worker quad 1 (NE) — legacy Phase 3 on Paper benches");
        LOG.info("  Thread 5  → Chassis worker quad 2 (SW) — legacy Phase 3 on Paper benches");
        LOG.info("  Thread 6  → Chassis worker quad 3 (SE) — legacy Phase 3 on Paper benches");
        LOG.info("  Thread 7  → Chunk Sync DLM (Paper Phase 3 legacy)");
        LOG.info("  Thread 8  → Boundary Sync (Paper Phase 3 legacy)");
        LOG.info("  Thread 9  → Compatibility Bridge");
        LOG.info("  Thread 10 → UI Sandbox 0 (menus / inventory)");
        LOG.info("  Thread 11 → UI Sandbox 1 (scoreboard / bossbar)");
        LOG.info("  Thread 12 → Heavy I/O 0 (database)");
        LOG.info("  Thread 13 → Heavy I/O 1 (world save)");
        LOG.info("  Thread 14 → Heavy I/O 2 (resource packs)");
        LOG.info("  Thread 15 → Heavy I/O 3 (Bedrock / floodgate)");
        LOG.info("  Thread 16 → Async Worker / Telemetry");
    }

    public boolean runItemClickSimulation() throws InterruptedException {
        long before = gameCore.getInventoryOps();
        long bridgeBefore = bridge.getDrained();

        trafficCop.ingest("STORE_CLICK", "Steve", Map.of(
                "item", "diamond_sword",
                "slot", "13",
                "x", "8",
                "z", "-8"
        ));

        CountDownLatch done = new CountDownLatch(1);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (System.nanoTime() < deadline) {
            if (gameCore.getInventoryOps() > before || bridge.getDrained() > bridgeBefore) {
                done.countDown();
                break;
            }
            Thread.sleep(25);
        }

        boolean ok = done.getCount() == 0;
        SpatialQuadrant q = BitwiseQuadrantIndex.fromBlock(8, -8);
        if (ok) {
            LOG.info("SIMULATION SUCCESS — item click reached Game Core"
                    + " | inventoryOps=" + gameCore.getInventoryOps()
                    + " | bridgeDrained=" + bridge.getDrained()
                    + " | ticks=" + gameCore.totalTicks()
                    + " | quadrant=" + q
                    + " | boundary=" + syncLayer.getProcessed()
                    + " | dlmGranted=" + syncLayer.dlm().grantedCount()
                    + " | telemetry=" + sandbox.telemetry().snapshotCount()
                    + " | compressor=" + trafficCop.compressor().name());
        } else {
            LOG.warning("SIMULATION TIMEOUT — ops=" + gameCore.getInventoryOps()
                    + " drained=" + bridge.getDrained());
        }
        return ok;
    }
}
