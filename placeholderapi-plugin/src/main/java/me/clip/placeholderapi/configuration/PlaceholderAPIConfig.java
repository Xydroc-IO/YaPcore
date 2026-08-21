package me.clip.placeholderapi.configuration;

import me.clip.placeholderapi.PlaceholderAPIPlugin;
import org.jetbrains.annotations.NotNull;

/** Minimal config facade matching clip PlaceholderAPIPlugin.getPlaceholderAPIConfig(). */
public final class PlaceholderAPIConfig {

    private final PlaceholderAPIPlugin plugin;

    public PlaceholderAPIConfig(@NotNull PlaceholderAPIPlugin plugin) {
        this.plugin = plugin;
    }

    @NotNull
    public String booleanTrue() {
        return plugin.getConfig().getString("boolean.true", "yes");
    }

    @NotNull
    public String booleanFalse() {
        return plugin.getConfig().getString("boolean.false", "no");
    }

    @NotNull
    public String dateFormat() {
        return plugin.getConfig().getString("date-format", "MM/dd/yy HH:mm:ss");
    }

    public boolean checkUpdates() {
        return plugin.getConfig().getBoolean("check-updates", false);
    }

    public boolean isCloudEnabled() {
        // YaP ships built-ins; eCloud downloads are not mirrored. Drop jars into expansions/.
        return plugin.getConfig().getBoolean("cloud-enabled", false);
    }
}
