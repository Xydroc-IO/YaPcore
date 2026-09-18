package com.yapcore.essentials.listener;

import com.yapcore.essentials.EssentialsPlugin;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Extends {@code block_interaction_range} so place/break is not vanilla 4.5/5.
 * Runs after YaPTailor’s join apply (20 ticks) so the catalog 5.0 does not win.
 */
public final class BlockReachListener implements Listener {

    private static final double VANILLA_SURVIVAL = 4.5;
    private static final double VANILLA_CREATIVE = 5.0;

    private final EssentialsPlugin plugin;

    public BlockReachListener(EssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        YapSched.entityLater(plugin, player, () -> apply(player), 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        GameMode next = event.getNewGameMode();
        YapSched.entityLater(plugin, player, () -> apply(player, next), 1L);
    }

    public void applyOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            YapSched.entity(plugin, player, () -> apply(player));
        }
    }

    public void apply(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        apply(player, player.getGameMode());
    }

    private void apply(Player player, GameMode mode) {
        FileConfiguration c = plugin.getConfig();
        if (!c.getBoolean("block-reach.enabled", true)) {
            return;
        }
        if (mode == GameMode.SPECTATOR) {
            return;
        }
        double reach = reachFor(c, mode);
        if (reach <= 0.0) {
            return;
        }
        try {
            AttributeInstance inst = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
            if (inst != null) {
                inst.setBaseValue(reach);
            }
        } catch (Throwable ignored) {
            // pre-1.20.5
        }
    }

    static double reachFor(FileConfiguration c, GameMode mode) {
        double survival = clamp(c.getDouble("block-reach.survival", 6.5));
        double creative = clamp(c.getDouble("block-reach.creative", 8.0));
        double adventure = clamp(c.getDouble("block-reach.adventure", 6.5));
        return switch (mode) {
            case CREATIVE -> creative > 0.0 ? creative : VANILLA_CREATIVE;
            case ADVENTURE -> adventure > 0.0 ? adventure : VANILLA_SURVIVAL;
            default -> survival > 0.0 ? survival : VANILLA_SURVIVAL;
        };
    }

    private static double clamp(double raw) {
        if (raw <= 0.0) {
            return 0.0;
        }
        return Math.max(1.0, Math.min(16.0, raw));
    }
}
