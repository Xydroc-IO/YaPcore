package com.yapcore.plugin;

import com.yapcore.bridge.CompatibilityBridge;
import com.yapcore.core.GameCore;
import com.yapcore.model.GameEvent;
import com.yapcore.util.ThreadMetrics;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * YaPcore dual-pool sandbox aligned with YapEngine v1.1:
 * <ul>
 *   <li>UI Sandboxes (2): menus / HUD</li>
 *   <li>Heavy I/O (4): DB, world, packs, Bedrock lanes</li>
 * </ul>
 * Primary 16-thread engine lives in {@link com.yaplabs.yapengine.YapEngine}.
 */
public final class PluginSandboxPool {

    private static final Logger LOG = Logger.getLogger("YaPcore.PluginPool");

    private final ExecutorService highSpeedPool;
    private final ExecutorService heavyIoPool;
    private final CompatibilityBridge bridge;
    private final GameCore gameCore;
    private final AtomicLong uiTasks = new AtomicLong();
    private final AtomicLong ioTasks = new AtomicLong();

    // Simulated player balances for the store demo
    private final AtomicLong demoBalanceCents = new AtomicLong(5_000);

    public PluginSandboxPool(CompatibilityBridge bridge, GameCore gameCore) {
        this.bridge = bridge;
        this.gameCore = gameCore;
        this.highSpeedPool = Executors.newFixedThreadPool(2, uiFactory());
        this.heavyIoPool = Executors.newFixedThreadPool(4, ioFactory());
    }

    public void shutdown() {
        highSpeedPool.shutdownNow();
        heavyIoPool.shutdownNow();
        ThreadMetrics.record("PluginSandboxPool", "shutdown");
    }

    public void submitUiTask(Runnable task) {
        uiTasks.incrementAndGet();
        highSpeedPool.execute(() -> {
            ThreadMetrics.record("PluginUI", "task-start");
            try {
                task.run();
            } finally {
                ThreadMetrics.record("PluginUI", "task-done");
            }
        });
    }

    public void submitHeavyIo(Runnable task) {
        ioTasks.incrementAndGet();
        heavyIoPool.execute(() -> {
            ThreadMetrics.record("PluginIO", "task-start");
            try {
                task.run();
            } finally {
                ThreadMetrics.record("PluginIO", "task-done");
            }
        });
    }

    /**
     * High-speed GUI click / store routing. Database verification is offloaded
     * to the Heavy I/O pool so UI and GameCore never block.
     */
    public void handleGuiEvent(GameEvent event) {
        String item = event.payload("item");
        String slot = event.payload("slot");
        LOG.info(() -> "UI pool rendering response for " + event.getPlayerName()
                + " click slot=" + slot + " item=" + item);

        // Instant UI feedback on high-speed pool
        renderStorePage(event.getPlayerName(), item);

        if (event.getType() == GameEvent.Type.STORE_PURCHASE_REQUEST
                || "buy".equalsIgnoreCase(event.payload("action"))) {
            String player = event.getPlayerName();
            String sku = item != null ? item : "unknown";
            submitHeavyIo(() -> verifyAndFulfillPurchase(player, sku));
        }
    }

    private void renderStorePage(String player, String highlightItem) {
        // Simulated multi-page store / text animation work
        ThreadMetrics.bump("PluginUI", "render-store");
        LOG.info(() -> "Store page rendered for " + player
                + (highlightItem != null ? " (highlight=" + highlightItem + ")" : ""));
    }

    /**
     * Heavy I/O: pretend MySQL/Mongo verification (~latency), then stage a
     * thread-safe inventory mutation through the Compatibility Bridge.
     */
    private void verifyAndFulfillPurchase(String player, String sku) {
        try {
            ThreadMetrics.bump("PluginIO", "db-verify-start");
            // Simulated remote DB round-trip — stays isolated on Heavy I/O lanes
            Thread.sleep(200);

            long price = priceFor(sku);
            long balance = demoBalanceCents.get();
            boolean ok = balance >= price;
            if (ok) {
                demoBalanceCents.addAndGet(-price);
            }
            ThreadMetrics.bump("PluginIO", ok ? "db-verify-ok" : "db-verify-denied");

            // Compile transaction log on heavy pool
            LOG.info(() -> "TX log: player=" + player + " sku=" + sku
                    + " price=" + price + " approved=" + ok);

            if (ok) {
                // Never touch game memory here — hand off via Compatibility Bridge
                bridge.submitLegacyMutation(
                        "StorePlugin",
                        "addItem:" + sku + "->" + player,
                        () -> {
                            gameCore.inventoryMutationCounter().incrementAndGet();
                            LOG.info(() -> "GameCore applied inventory add: "
                                    + sku + " for " + player
                                    + " (mutations=" + gameCore.getInventoryMutations() + ")");
                        });

                // UI confirmation back on high-speed pool
                submitUiTask(() -> {
                    ThreadMetrics.bump("PluginUI", "purchase-success-anim");
                    LOG.info(() -> "UI success animation for " + player + " / " + sku);
                });
            } else {
                submitUiTask(() -> {
                    ThreadMetrics.bump("PluginUI", "purchase-denied-anim");
                    LOG.info(() -> "UI denial animation for " + player);
                });
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static long priceFor(String sku) {
        return switch (sku == null ? "" : sku.toLowerCase()) {
            case "diamond_sword" -> 1_500;
            case "ender_pearl" -> 250;
            case "golden_apple" -> 500;
            default -> 100;
        };
    }

    public long getUiTaskCount() {
        return uiTasks.get();
    }

    public long getIoTaskCount() {
        return ioTasks.get();
    }

    public long getDemoBalanceCents() {
        return demoBalanceCents.get();
    }

    /** Demo helper to seed a store click through the same path TrafficCop uses. */
    public Map<String, String> demoStorePayload(String item, String slot) {
        return Map.of(
                "item", item,
                "slot", slot,
                "action", "buy",
                "page", "1"
        );
    }

    private static ThreadFactory uiFactory() {
        AtomicInteger seq = new AtomicInteger(10);
        return r -> {
            int id = seq.getAndIncrement();
            String name = id == 10 ? "yap-t10-ui-menu" : "yap-t11-ui-hud";
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }

    private static ThreadFactory ioFactory() {
        String[] names = {
                "yap-t12-io-database",
                "yap-t13-io-world",
                "yap-t14-io-packs",
                "yap-t15-io-bedrock"
        };
        AtomicInteger seq = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, names[seq.getAndIncrement() % names.length]);
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY + 1);
            return t;
        };
    }
}
