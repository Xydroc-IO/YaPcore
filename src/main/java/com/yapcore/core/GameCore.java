package com.yapcore.core;

import com.yapcore.bridge.CompatibilityBridge;
import com.yapcore.model.GameEvent;
import com.yapcore.util.ThreadMetrics;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Thread 3 — Main Game Core (The Heartbeat).
 * Strict 20 TPS loop for physics, chunks, blocks, and entities.
 * Networking and plugin I/O stay off this thread. At the end of each 50ms tick,
 * opens a handoff window to drain CompatibilityBridge tasks.
 */
public class GameCore implements Runnable {

    public static final int TPS = 20;
    public static final long TICK_NANOS = 1_000_000_000L / TPS; // 50ms

    private static final Logger LOG = Logger.getLogger("YaPcore.GameCore");

    private final ConcurrentLinkedQueue<GameEvent> inboundEvents;
    private final CompatibilityBridge bridge;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong tickCounter = new AtomicLong();
    private final AtomicLong lastTickNanos = new AtomicLong(System.nanoTime());
    private volatile Thread coreThread;
    private volatile boolean worldPaused;

    // Simulated world counters for the demo
    private final AtomicLong entityUpdates = new AtomicLong();
    private final AtomicLong chunkTicks = new AtomicLong();
    private final AtomicLong inventoryMutations = new AtomicLong();

    public GameCore(ConcurrentLinkedQueue<GameEvent> inboundEvents, CompatibilityBridge bridge) {
        this.inboundEvents = inboundEvents;
        this.bridge = bridge;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        worldPaused = false;
        coreThread = new Thread(this, "yap-core3-game-core");
        coreThread.setDaemon(false);
        coreThread.start();
        ThreadMetrics.record("GameCore", "started");
    }

    public void stop() {
        running.set(false);
        if (coreThread != null) {
            coreThread.interrupt();
        }
        ThreadMetrics.record("GameCore", "stopped");
    }

    public void pauseWorld() {
        worldPaused = true;
        ThreadMetrics.record("GameCore", "world-paused");
    }

    public void resumeWorld() {
        worldPaused = false;
        ThreadMetrics.record("GameCore", "world-resumed");
    }

    public void emergencySave() {
        LOG.warning("EMERGENCY WORLD SAVE — flushing chunks & inventories");
        ThreadMetrics.record("GameCore", "emergency-save");
    }

    public Thread getCoreThread() {
        return coreThread;
    }

    public long getTickCounter() {
        return tickCounter.get();
    }

    public long millisSinceLastTick() {
        return (System.nanoTime() - lastTickNanos.get()) / 1_000_000L;
    }

    public long getInventoryMutations() {
        return inventoryMutations.get();
    }

    public AtomicLong inventoryMutationCounter() {
        return inventoryMutations;
    }

    @Override
    public void run() {
        LOG.info("Main Game Core online @ " + TPS + " TPS");
        while (running.get()) {
            long tickStart = System.nanoTime();
            try {
                if (!worldPaused) {
                    runPhysicsTick();
                    consumeInboundEvents();
                }
                // Handoff window: resolve Compatibility Bridge tasks at tick boundary
                int drained = bridge.drainForTick();
                if (drained > 0) {
                    ThreadMetrics.bump("GameCore", "bridge-handoff");
                }

                tickCounter.incrementAndGet();
                lastTickNanos.set(System.nanoTime());

                long elapsed = System.nanoTime() - tickStart;
                long sleepNanos = TICK_NANOS - elapsed;
                if (sleepNanos > 0) {
                    Thread.sleep(sleepNanos / 1_000_000L, (int) (sleepNanos % 1_000_000L));
                } else {
                    ThreadMetrics.bump("GameCore", "tick-overrun");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (RuntimeException ex) {
                LOG.severe("GameCore tick fault: " + ex.getMessage());
                ThreadMetrics.bump("GameCore", "tick-fault");
            }
        }
        LOG.info("Main Game Core shut down after " + tickCounter.get() + " ticks");
    }

    private void runPhysicsTick() {
        // Simulated sequential world work — never touches Netty or plugin I/O
        chunkTicks.incrementAndGet();
        entityUpdates.addAndGet(3);
        if (tickCounter.get() % 20 == 0) {
            ThreadMetrics.bump("GameCore", "physics-second");
        }
    }

    private void consumeInboundEvents() {
        GameEvent event;
        int processed = 0;
        while ((event = inboundEvents.poll()) != null) {
            processed++;
            switch (event.getType()) {
                case PLAYER_MOVE, PLAYER_CHAT, COMMAND, HEARTBEAT ->
                        ThreadMetrics.bump("GameCore", "event");
                case GUI_CLICK, STORE_PURCHASE_REQUEST ->
                        ThreadMetrics.bump("GameCore", "ack");
                case CLIENT_JOIN, CLIENT_LEAVE, CLIENT_REJECTED,
                     RESOURCE_PACK_OFFER, RESOURCE_PACK_STATUS ->
                        ThreadMetrics.bump("GameCore", "client");
                default -> ThreadMetrics.bump("GameCore", "unknown-event");
            }
        }
        if (processed > 0) {
            ThreadMetrics.bump("GameCore", "events-processed");
        }
    }
}
