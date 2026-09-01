package com.yapcore.world.listener;

import com.yapcore.world.WorldPlugin;
import com.yapcore.world.cmd.WorldEditOps;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Arrays;
import java.util.Locale;

/**
 * Classic WorldEdit {@code //} aliases ({@code //set}, {@code //copy}, …).
 */
public final class WorldEditSlashBridge implements Listener {

    private final WorldPlugin plugin;

    public WorldEditSlashBridge(WorldPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message == null || message.length() < 3 || !message.startsWith("//")) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("yapworld.selection") && !player.hasPermission("yapworld.brush")
                && !player.hasPermission("yapworld.admin")) {
            return;
        }
        WorldEditOps ops = plugin.editOps();
        if (ops == null) {
            return;
        }
        String body = message.substring(2).trim();
        if (body.isEmpty()) {
            event.setCancelled(true);
            ops.help(player);
            return;
        }
        String[] parts = body.split("\\s+");
        String name = parts[0].toLowerCase(Locale.ROOT);
        String[] args = parts.length > 1 ? Arrays.copyOfRange(parts, 1, parts.length) : new String[0];
        if (ops.dispatch(player, name, args)) {
            event.setCancelled(true);
        }
    }
}
