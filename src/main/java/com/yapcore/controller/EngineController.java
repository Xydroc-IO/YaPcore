package com.yapcore.controller;

import com.yapcore.core.GameCore;
import com.yapcore.crash.CrashLogger;
import com.yapcore.network.TrafficCop;
import com.yapcore.util.ThreadMetrics;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Thread 1 — The Controller (Watchdog / Health Monitor).
 * Samples stack traces of Cores 2, 3, and 4. On lockup (&gt; 2s without a GameCore tick),
 * pauses traffic, triggers emergency save, and resumes safely.
 */
public final class EngineController implements Runnable {

    private static final Logger LOG = Logger.getLogger("YaPcore.Controller");
    private static final long LOCKUP_THRESHOLD_MS = 2_000L;
    private static final long SAMPLE_INTERVAL_MS = 250L;

    private final GameCore gameCore;
    private final TrafficCop trafficCop;
    private final Supplier<Thread> bridgeThreadSupplier;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Thread controllerThread;
    private volatile boolean recoveryInProgress;

    public EngineController(GameCore gameCore,
                            TrafficCop trafficCop,
                            Supplier<Thread> bridgeThreadSupplier) {
        this.gameCore = gameCore;
        this.trafficCop = trafficCop;
        this.bridgeThreadSupplier = bridgeThreadSupplier;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        controllerThread = new Thread(this, "yap-core1-controller");
        controllerThread.setDaemon(false);
        controllerThread.start();
        ThreadMetrics.record("EngineController", "started");
    }

    public void stop() {
        running.set(false);
        if (controllerThread != null) {
            controllerThread.interrupt();
        }
        ThreadMetrics.record("EngineController", "stopped");
    }

    public Thread getControllerThread() {
        return controllerThread;
    }

    @Override
    public void run() {
        LOG.info("Controller watchdog online — sampling cores 2/3/4");
        while (running.get()) {
            try {
                sampleAndRecover();
                Thread.sleep(SAMPLE_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        LOG.info("Controller shut down");
    }

    private void sampleAndRecover() {
        Thread core2 = trafficCop.getCopThread();
        Thread core3 = gameCore.getCoreThread();
        Thread core4 = bridgeThreadSupplier.get();

        sampleStack("TrafficCop", core2);
        sampleStack("GameCore", core3);
        sampleStack("CompatibilityBridge", core4);

        long stallMs = gameCore.millisSinceLastTick();
        if (stallMs > LOCKUP_THRESHOLD_MS && !recoveryInProgress && core3 != null && core3.isAlive()) {
            LOG.severe("LOCKUP DETECTED: GameCore stalled " + stallMs + "ms — beginning recovery");
            ThreadMetrics.record("EngineController", "lockup-detected");
            CrashLogger.get().logWatchdogRecovery("gamecore-stall", Map.of(
                    "stall-ms", Long.toString(stallMs),
                    "ticks", Long.toString(gameCore.getTickCounter())
            ));
            performSafeRecovery();
        }
    }

    private void sampleStack(String label, Thread thread) {
        if (thread == null || !thread.isAlive()) {
            return;
        }
        thread.getStackTrace();
        ThreadMetrics.bump("EngineController", "sample:" + label);
    }

    private void performSafeRecovery() {
        recoveryInProgress = true;
        try {
            trafficCop.pauseTraffic();
            gameCore.pauseWorld();
            gameCore.emergencySave();

            Thread recoveryWindow = new Thread(() -> {
                ThreadMetrics.record("EngineController", "recovery-window");
                LOG.warning("Recovery window: backloading inventories to database…");
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "yap-recovery-window");
            recoveryWindow.start();
            try {
                recoveryWindow.join(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            gameCore.resumeWorld();
            trafficCop.resumeTraffic();
            ThreadMetrics.record("EngineController", "recovery-complete");
            LOG.warning("Recovery complete — traffic and GameCore resumed");
        } finally {
            recoveryInProgress = false;
        }
    }
}
