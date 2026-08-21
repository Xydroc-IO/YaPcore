package com.yapcore.stacker;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** PlaceholderAPI expansion: {@code %yapstacker_<id>%}. */
public final class StackerPlaceholders extends PlaceholderExpansion {

    private final StackerPlugin plugin;

    public StackerPlaceholders(StackerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "yapstacker";
    }

    @Override
    public @NotNull String getAuthor() {
        return "YapLabs";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        StackerMetrics m = plugin.metrics();
        StackerConfig c = plugin.stackerConfig();
        return switch (params.toLowerCase()) {
            case "enabled" -> Boolean.toString(c.enabled());
            case "kill_mode", "killmode" -> c.killMode().name();
            case "mob_merges", "merges" -> Long.toString(m.mobMerges());
            case "mob_kills", "kills" -> Long.toString(m.mobKillsProcessed());
            case "item_merges" -> Long.toString(m.itemMerges());
            case "spawner_stacks", "spawners" -> Long.toString(m.spawnerStacks());
            case "aura_kills" -> Long.toString(m.auraKills());
            case "max_stack" -> Integer.toString(c.maxStack());
            case "merge_radius" -> Double.toString(c.mergeRadius());
            default -> null;
        };
    }
}
