package com.yapcore.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Declares what plugin/module authors can rely on today.
 * <p>
 * <b>Product path</b> ({@code game-authority=paper}): Paper plugins get
 * <em>complete</em> Paper API coverage from the embedded Paperclip
 * ({@code paper-api} 26.2 — same surface as stock Paper). YaP stubs never
 * shadow that classpath ({@code Phase3PaperClassLoader} uses the platform parent).
 * <p>
 * <b>Facade path</b> (non-Paper authority / {@code yap.yml} bridge): best-effort
 * stubs only — not bit-identical Paper method bodies.
 */
public final class ApiCoverage {

    private ApiCoverage() {
    }

    public enum Status { FULL, PARTIAL, STUB, PLANNED }

    public record Entry(String area, Status status, String notes) {
    }

    public static List<Entry> snapshot() {
        return List.of(
                // --- Product path (default): real Paper owns the API ---
                new Entry("Paper API (game-authority=paper)", Status.FULL,
                        "Complete — embedded Paperclip paper-api 26.2 (~2329 classes); same as stock Paper"),
                new Entry("JavaPlugin + plugin.yml / paper-plugin.yml", Status.FULL,
                        "Loaded by real Paper via plugins/ → paper-kernel/plugins symlink"),
                new Entry("Paper PluginClassLoader", Status.FULL,
                        "Paper’s loader; YaP stubs isolated (platform parent)"),
                new Entry("ServicesManager / Vault-style", Status.FULL,
                        "Real Paper ServicesManager"),
                new Entry("Bukkit scheduler + Paper RegionScheduler-free APIs", Status.FULL,
                        "Stock Paper schedulers (not Folia)"),
                new Entry("Paper/Bukkit events + Adventure", Status.FULL,
                        "Full Paper event catalog + Kyori Adventure from Paper"),
                new Entry("Brigadier (Paper Commands registrar)", Status.FULL,
                        "Real Paper brigadier command API"),
                new Entry("CraftBukkit / NMS (Paper mappings)", Status.FULL,
                        "Same as stock Paper 26.2 / YaP Paperclip overlays"),
                new Entry("Inventory / ItemMeta / Player / World / perms / messaging", Status.FULL,
                        "Complete under Paper authority"),
                new Entry("Plugin back-compat 1.20–1.21 → 26.2", Status.PARTIAL,
                        "Tier A+B: Enchantment/Potion/Particle fields + CraftBukkit v1_20/v1_21 packages"),
                new Entry("Chunk pregen (yap-pregen)", Status.FULL,
                        "Chunky-class shapes, multi-world, WorldEdit sel, dashboard Pregen tab"),

                // --- YaP-native surface (always) ---
                new Entry("YaPPlugin + yap.yml", Status.FULL,
                        "Native dual-pool plugins in plugins/"),
                new Entry("YaPModule + module.yml", Status.FULL,
                        "Fine-tune modules in modules/"),
                new Entry("YaPScheduler UI/HEAVY/SYNC", Status.FULL,
                        "ThreadPools ownership for YaP plugins/modules"),

                // --- Facade only (non-Paper authority) ---
                new Entry("Facade: Paper type stubs (non-Paper authority)", Status.STUB,
                        "Skeletal org.bukkit.*/io.papermc.* for soft-fail; not product path"),
                new Entry("Facade: bit-identical Paper method bodies", Status.PLANNED,
                        "Not a goal while Paper is game authority — use Paper"),
                new Entry("Folia RegionScheduler APIs", Status.PLANNED,
                        "Unsupported — YaPcore is not Folia")
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
        return "Paper plugins: complete Paper API via embedded Paper 26.2 "
                + "(game-authority=paper). Facade stubs are non-product only.";
    }
}
