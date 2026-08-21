package com.yapcore.paper.phase3;

import com.yaplabs.yapengine.core.spatial.ParallelGameCore;
import com.yaplabs.yapengine.core.spatial.SpatialGameLoop;
import com.yaplabs.yapengine.core.spatial.SpatialQuadrant;
import com.yaplabs.yapengine.sequencing.SequenceToken;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Phase 3 scaffold — route work onto YapEngine spatial cores 3–6 while Paper
 * still owns the real game tick.
 * <p>
 * Bridge / observability layer for now. Full Paper tick conversion comes after
 * Paper sources are vendored.
 */
public final class PaperTickBridge {

    private static final Logger LOG = Logger.getLogger("YaPcore.Paper.Phase3");

    private final ParallelGameCore gameCore;
    private final AtomicBoolean online = new AtomicBoolean(false);
    private final AtomicLong handedOff = new AtomicLong();

    public PaperTickBridge(ParallelGameCore gameCore) {
        this.gameCore = Objects.requireNonNull(gameCore, "gameCore");
    }

    public void start() {
        if (!online.compareAndSet(false, true)) {
            return;
        }
        LOG.info("Phase 3 PaperTickBridge online — spatial handoff API ready "
                + "(interior entity tick via leases when NMS path enabled)");
    }

    public void stop() {
        online.set(false);
        LOG.info("Phase 3 PaperTickBridge stopped (handedOff=" + handedOff.get() + ")");
    }

    public boolean isOnline() {
        return online.get();
    }

    public long handedOffCount() {
        return handedOff.get();
    }

    /**
     * Enqueue work on the spatial core for block XZ.
     * Safe from Paper main / Netty; runs on cores 3–6.
     */
    public void submitBlockWork(int blockX, int blockZ, String label, Runnable action) {
        if (!online.get()) {
            action.run();
            return;
        }
        SequenceToken token = SequenceToken.next("phase3:" + label);
        gameCore.dispatch(blockX, blockZ, token, label, () -> {
            handedOff.incrementAndGet();
            action.run();
        });
    }

    public void submitQuadrant(SpatialQuadrant quadrant, String label, Runnable action) {
        if (!online.get()) {
            action.run();
            return;
        }
        SequenceToken token = SequenceToken.next("phase3:" + quadrant.name());
        gameCore.loop(quadrant).enqueue(new SpatialGameLoop.SequencedTask(token, () -> {
            handedOff.incrementAndGet();
            action.run();
        }, label));
    }
}
