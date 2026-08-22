package com.yapcore;

import com.yapcore.bridge.ForwardingCompatibilityBridge;
import com.yapcore.console.ConsoleBus;
import com.yapcore.model.GameEvent;
import com.yapcore.network.TrafficCop;
import com.yapcore.plugin.PluginSandboxPool;
import com.yapcore.util.ThreadMetrics;
import com.yaplabs.yapengine.YapEngine;
import com.yaplabs.yapengine.core.spatial.ParallelGameCore;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntSupplier;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * YaPcore facade over the YapLabs slim-chassis {@link YapEngine}.
 * Folia (embedded child JVM) owns game tick on the product path.
 */
public final class YaPcoreEngine {

    private static final Logger LOG = Logger.getLogger("YaPcore");

    private final YapEngine yapEngine = new YapEngine();
    private final ConcurrentLinkedQueue<GameEvent> eventStream = new ConcurrentLinkedQueue<>();
    private final ForwardingCompatibilityBridge facadeBridge =
            new ForwardingCompatibilityBridge(yapEngine::bridge);
    private final PluginSandboxPool pluginPool;
    private final TrafficCop trafficCop;
    private final AtomicInteger maxPlayers = new AtomicInteger(100);
    private volatile IntSupplier onlinePlayers = () -> 0;
    private volatile Thread relayThread;

    public YaPcoreEngine() {
        this(25566);
    }

    public YaPcoreEngine(int port) {
        CoreOpsAdapter coreOps = new CoreOpsAdapter();
        this.pluginPool = new PluginSandboxPool(facadeBridge, coreOps);
        this.trafficCop = new TrafficCop(eventStream, pluginPool, port, false);
    }

    public YapEngine yapEngine() {
        return yapEngine;
    }

    public ParallelGameCore parallelGameCore() {
        return yapEngine.gameCore();
    }

    public void setMaxPlayers(int max) {
        maxPlayers.set(Math.max(1, max));
    }

    public int getMaxPlayers() {
        return maxPlayers.get();
    }

    public void setPlayerCountSupplier(IntSupplier supplier) {
        this.onlinePlayers = supplier != null ? supplier : () -> 0;
    }

    public int getOnlinePlayers() {
        return onlinePlayers.getAsInt();
    }

    public boolean isFull() {
        return getOnlinePlayers() >= getMaxPlayers();
    }

    public void start() {
        LOG.info("=== YaPcore boot → YapEngine chassis (Folia = game tick) ===");
        yapEngine.start();
        facadeBridge.start();
        trafficCop.start();
        relayThread = new Thread(this::relayLoop, "yap-event-relay");
        relayThread.setDaemon(true);
        relayThread.start();
        LOG.info("YaPcore facade online (YapEngine edge/I/O + dual-stack relay)");
    }

    public void stop() {
        LOG.info("=== YaPcore / YapEngine shutdown ===");
        if (relayThread != null) {
            relayThread.interrupt();
        }
        trafficCop.stop();
        facadeBridge.stop();
        yapEngine.stop();
        pluginPool.shutdown();
        ThreadMetrics.dumpSummary();
    }

    public TrafficCop trafficCop() {
        return trafficCop;
    }

    public GameCoreView gameCore() {
        return new GameCoreView(yapEngine.gameCore());
    }

    public com.yapcore.bridge.CompatibilityBridge bridge() {
        return facadeBridge;
    }

    public PluginSandboxPool pluginPool() {
        return pluginPool;
    }

    public boolean runLifecycleDemo() throws InterruptedException {
        LOG.info("--- Lifecycle demo via YapEngine (UI → Heavy → Bridge → Spatial Core) ---");
        return yapEngine.runItemClickSimulation();
    }

    private void relayLoop() {
        while (!Thread.currentThread().isInterrupted() && yapEngine.isRunning()) {
            GameEvent event = eventStream.poll();
            if (event == null) {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }
            String type = switch (event.getType()) {
                case GUI_CLICK, STORE_PURCHASE_REQUEST -> "STORE_CLICK";
                case PLAYER_MOVE -> "MOVE";
                default -> event.getType().name();
            };
            yapEngine.trafficCop().ingest(type, event.getPlayerName(), event.getPayload());
        }
    }

    public static void configureLogging() {
        Logger root = Logger.getLogger("");
        root.setLevel(Level.INFO);
        for (var handler : root.getHandlers()) {
            root.removeHandler(handler);
        }
        ConsoleHandler console = new ConsoleHandler();
        console.setLevel(Level.INFO);
        console.setFormatter(new SimpleFormatter());
        root.addHandler(console);
        ConsoleBus.get().installAsJulHandler();
    }

    public static final class GameCoreView {
        private final ParallelGameCore core;

        GameCoreView(ParallelGameCore core) {
            this.core = core;
        }

        public long getTickCounter() {
            return core.totalTicks();
        }

        public long getInventoryMutations() {
            return core.getInventoryOps();
        }

        public AtomicLong inventoryMutationCounter() {
            return new AtomicLong(core.getInventoryOps());
        }
    }

    private final class CoreOpsAdapter extends com.yapcore.core.GameCore {
        private final AtomicLong mutations = new AtomicLong();

        CoreOpsAdapter() {
            super(new ConcurrentLinkedQueue<>(), facadeBridge);
        }

        @Override
        public long getInventoryMutations() {
            return yapEngine.gameCore().getInventoryOps() + mutations.get();
        }

        @Override
        public AtomicLong inventoryMutationCounter() {
            return mutations;
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }
    }
}
