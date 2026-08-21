package com.yaplabs.yapengine.controller;

import com.yaplabs.yapengine.core.spatial.ParallelGameCore;
import com.yaplabs.yapengine.core.spatial.SpatialGameLoop;
import com.yaplabs.yapengine.core.spatial.SpatialQuadrant;
import com.yaplabs.yapengine.network.traffic.TrafficCop;
import com.yaplabs.yapengine.sandbox.telemetry.TelemetryWorker;
import com.yaplabs.yapengine.sync.boundary.BoundaryArbitrator;
import com.yaplabs.yapengine.sync.dlm.ChunkSyncDlm;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/** Thread 1 — Controller / Watchdog (v1.1 health over spatial + DLM + boundary + telemetry). */
public final class EngineController implements Runnable {

    private static final Logger LOG = Logger.getLogger("YapEngine.Controller");
    private static final long LOCKUP_MS = 2_000L;

    private final ParallelGameCore gameCore;
    private final TrafficCop trafficCop;
    private final ChunkSyncDlm dlm;
    private final BoundaryArbitrator boundary;
    private final TelemetryWorker telemetry;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Thread thread;

    public EngineController(ParallelGameCore gameCore,
                            TrafficCop trafficCop,
                            ChunkSyncDlm dlm,
                            BoundaryArbitrator boundary,
                            TelemetryWorker telemetry) {
        this.gameCore = gameCore;
        this.trafficCop = trafficCop;
        this.dlm = dlm;
        this.boundary = boundary;
        this.telemetry = telemetry;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        thread = new Thread(this, "yap-t1-controller");
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
        LOG.info("Controller online (Thread 1 / watchdog)");
    }

    public void stop() {
        running.set(false);
        if (thread != null) {
            thread.interrupt();
        }
    }

    public Thread getThread() {
        return thread;
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                sample();
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        LOG.info("Controller shut down");
    }

    private void sample() {
        touch(trafficCop.getThread());
        touch(dlm.getThread());
        touch(boundary.getThread());
        touch(telemetry.getThread());
        for (SpatialQuadrant q : SpatialQuadrant.values()) {
            SpatialGameLoop loop = gameCore.loop(q);
            touch(loop.getThread());
        }
        long stall = gameCore.millisSinceAnyTick();
        if (stall > LOCKUP_MS) {
            LOG.severe("LOCKUP: spatial core stalled " + stall + "ms — emergency snapshot");
            trafficCop.pause();
            telemetry.offer(() -> LOG.warning("Emergency world-state snapshot committed"));
            trafficCop.resume();
        }
        if (boundary.pending() > 2_000) {
            LOG.warning("Boundary backlog=" + boundary.pending()
                    + " dlmPending=" + dlm.pending());
        }
    }

    private static void touch(Thread t) {
        if (t != null && t.isAlive()) {
            t.getStackTrace();
        }
    }
}
