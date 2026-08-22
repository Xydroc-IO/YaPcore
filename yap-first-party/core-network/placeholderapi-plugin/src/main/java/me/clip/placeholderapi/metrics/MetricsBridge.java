package me.clip.placeholderapi.metrics;

import me.clip.placeholderapi.PlaceholderAPIPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedPie;
import org.bstats.charts.SimplePie;

/** bStats charts for YaP PlaceholderAPI (service id distinct from HelpChat). */
public final class MetricsBridge {

    /** YaP PlaceholderAPI bStats service id. */
    private static final int SERVICE_ID = 23701;

    private MetricsBridge() {
    }

    public static void start(PlaceholderAPIPlugin plugin) {
        try {
            Metrics metrics = new Metrics(plugin, SERVICE_ID);
            metrics.addCustomChart(new SimplePie("using_expansion_cloud",
                    () -> plugin.getPlaceholderAPIConfig().isCloudEnabled() ? "yes" : "no"));
            metrics.addCustomChart(new SimplePie("using_spigot",
                    () -> PlaceholderAPIPlugin.getServerVersion().isSpigot() ? "yes" : "no"));
            metrics.addCustomChart(new AdvancedPie("expansions_used", () -> {
                java.util.Map<String, Integer> values = new java.util.HashMap<>();
                for (PlaceholderExpansion expansion : plugin.getLocalExpansionManager().getExpansions()) {
                    String key = expansion.getRequiredPlugin() == null
                            ? expansion.getIdentifier()
                            : expansion.getRequiredPlugin();
                    values.put(key, 1);
                }
                return values;
            }));
        } catch (Throwable t) {
            plugin.getLogger().warning("bStats init skipped: " + t.getMessage());
        }
    }
}
