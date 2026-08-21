package com.yaplabs.yapengine.sandbox;

import com.yaplabs.yapengine.bridge.CompatibilityBridge;
import com.yaplabs.yapengine.core.spatial.BitwiseQuadrantIndex;
import com.yaplabs.yapengine.core.spatial.ParallelGameCore;
import com.yaplabs.yapengine.core.spatial.SpatialQuadrant;
import com.yaplabs.yapengine.sandbox.io.HeavyIoSandbox;
import com.yaplabs.yapengine.sandbox.telemetry.TelemetryWorker;
import com.yaplabs.yapengine.sandbox.ui.UiSandboxPool;
import com.yaplabs.yapengine.sequencing.SequenceToken;

import java.util.logging.Logger;

/**
 * Dual-pool plugin sandboxes (v1.1): UI 10–11, Heavy I/O 12–15, Telemetry 16.
 */
public final class PluginSandbox {

    private static final Logger LOG = Logger.getLogger("YapEngine.Sandbox");

    private final CompatibilityBridge bridge;
    private final ParallelGameCore gameCore;
    private final UiSandboxPool ui = new UiSandboxPool();
    private final HeavyIoSandbox heavyIo = new HeavyIoSandbox();
    private final TelemetryWorker telemetry = new TelemetryWorker();

    public PluginSandbox(CompatibilityBridge bridge, ParallelGameCore gameCore) {
        this.bridge = bridge;
        this.gameCore = gameCore;
        telemetry.start();
    }

    public void shutdown() {
        telemetry.stop();
        ui.shutdown();
        heavyIo.shutdown();
    }

    public UiSandboxPool ui() {
        return ui;
    }

    public HeavyIoSandbox heavyIo() {
        return heavyIo;
    }

    public TelemetryWorker telemetry() {
        return telemetry;
    }

    public void runUi(Runnable task) {
        ui.execute(task);
    }

    public void runHeavy(Runnable task) {
        heavyIo.runAny(task);
    }

    public void simulateItemClick(String player, String itemId, int worldX, int worldZ) {
        SequenceToken clickToken = SequenceToken.next("player:" + player);
        LOG.info("UI click seq=" + clickToken.getStreamSeq()
                + " µs=" + clickToken.getIngestMicros()
                + " player=" + player + " item=" + itemId);

        ui.runMenu(() -> {
            LOG.info("[UI-10] render click feedback for " + player + " / " + itemId);
            ui.runHud(() -> LOG.info("[UI-11] scoreboard flash purchase " + itemId));

            heavyIo.runDatabase(() -> {
                LOG.info("[IO-12 DB] mock SQL verify purchase " + itemId);
                try {
                    Thread.sleep(120);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                SpatialQuadrant q = BitwiseQuadrantIndex.fromBlock(worldX, worldZ);
                bridge.submit("StorePlugin", "giveItem:" + itemId + "->" + player, q, () -> {
                    SequenceToken applyToken = SequenceToken.next("player:" + player);
                    gameCore.safeInventoryApply(
                            "inv:" + player,
                            worldX,
                            worldZ,
                            applyToken,
                            () -> {
                                LOG.info("[GameCore] applied " + itemId + " to " + player
                                        + " seq=" + applyToken.getStreamSeq()
                                        + " µs=" + applyToken.getIngestMicros());
                                telemetry.offer(() -> LOG.fine(
                                        "[T16] purchase metric player=" + player + " item=" + itemId));
                            }
                    );
                });
            });
        });
    }

    public long getUiTasks() {
        return ui.taskCount();
    }

    public long getIoTasks() {
        return heavyIo.totalTasks();
    }
}
