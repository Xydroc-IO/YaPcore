package com.yapcore.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Declares what plugin/module authors can rely on today.
 * <p>
 * <b>Product path</b> ({@code game-authority=folia}): Folia owns the game tick;
 * first-party plugins use {@code YapSched} + {@code folia-supported: true}.
 * YaP Link (own JVM) modern-forwards to Folia backends. Chassis owns Via/Geyser
 * dual-stack edge.
 * <p>
 * <b>Legacy</b> ({@code game-authority=paper}): Paper + optional Phase 3 spatial
 * benches only — not the product default.
 * <p>
 * <b>Facade path</b> (non-Folia/Paper authority / {@code yap.yml} bridge):
 * best-effort stubs only.
 */
public final class ApiCoverage {

    private ApiCoverage() {
    }

    public enum Status { FULL, PARTIAL, STUB, PLANNED, UNSUPPORTED }

    public record Entry(String area, Status status, String notes) {
    }

    public static List<Entry> snapshot() {
        return List.of(
                // --- Product path (default): Folia + chassis + YaP Link ---
                new Entry("Folia game authority (game-authority=folia)", Status.FULL,
                        "Managed Folia process owns JE tick (regionized); FoliaKernel embed"),
                new Entry("Folia-native first-party plugins", Status.FULL,
                        "folia-supported:true + YapSched (GlobalRegion/Entity/Region/Async)"),
                new Entry("plugins/ → folia-kernel/plugins", Status.FULL,
                        "Unified symlink packaging (ops convenience)"),
                new Entry("YaP Link modern forwarding", Status.FULL,
                        "Own JVM proxy; velocity:player_info HMAC → Folia paper-global.yml"),
                new Entry("Velocity stand-in (same contract)", Status.FULL,
                        "Stock Velocity still works; YaP Link is the product proxy"),
                new Entry("Folia RegionScheduler / EntityScheduler APIs", Status.FULL,
                        "Via Folia + YapSched in first-party plugins"),
                new Entry("Chassis Via* parity front", Status.PARTIAL,
                        "protocol-via-enabled → DualStackGateway ViaProxyHandler → Folia loopback"),
                new Entry("Chassis Geyser/Floodgate dual-stack", Status.PARTIAL,
                        "BE UDP on chassis; prefer Geyser on YaP Link edge for networks"),
                new Entry("GameCommandBridge (BE/console → Folia stdin)", Status.FULL,
                        "Managed Folia process dispatch; same-JVM Bukkit when available"),
                new Entry("Chunk pregen (yap-pregen)", Status.FULL,
                        "Folia-native YapSched timers; Chunky-class shapes"),

                // --- YaP-native surface (always) ---
                new Entry("YaPPlugin + yap.yml", Status.FULL,
                        "Native dual-pool plugins in plugins/"),
                new Entry("YaPModule + module.yml", Status.FULL,
                        "Fine-tune modules in modules/"),
                new Entry("YaPScheduler UI/HEAVY/SYNC", Status.FULL,
                        "ThreadPools ownership for YaP plugins/modules (chassis)"),

                // --- Legacy Paper (benches only) ---
                new Entry("Paper API (game-authority=paper, legacy)", Status.FULL,
                        "Embedded Paper paper-api 26.2 — legacy benches only"),
                new Entry("Stock Paper jars on Folia", Status.UNSUPPORTED,
                        "No PaperCompat shim — Folia-native / first-party only"),

                // --- Facade only ---
                new Entry("Facade: Paper type stubs (non-game authority)", Status.STUB,
                        "Skeletal org.bukkit.*/io.papermc.* for soft-fail; not product path")
        );
    }

    public static Map<String, String> asMap() {
        Map<String, String> m = new LinkedHashMap<>();
        for (Entry e : snapshot()) {
            m.put(e.area(), e.status() + " — " + e.notes());
        }
        return m;
    }

    /** Human-readable product claim for banners / docs generation. */
    public static String productClaim() {
        return "Product path: Folia game + Folia-native first-party plugins (YapSched) "
                + "+ YaP Link modern forwarding + chassis Via/Geyser. "
                + "Stock Paper jars unsupported. Legacy game-authority=paper for benches only.";
    }
}
