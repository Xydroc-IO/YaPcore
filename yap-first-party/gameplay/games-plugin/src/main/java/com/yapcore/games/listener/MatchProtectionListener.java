package com.yapcore.games.listener;

import com.yapcore.games.match.MatchManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public final class MatchProtectionListener implements Listener {

    private final MatchManager matches;

    public MatchProtectionListener(MatchManager matches) {
        this.matches = matches;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (matches.isInActiveMatch(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (matches.isInActiveMatch(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!matches.isInActiveMatch(player.getUniqueId())) {
            return;
        }
        String msg = event.getMessage().toLowerCase();
        if (msg.startsWith("/queue leave") || msg.startsWith("/queue")) {
            return;
        }
        if (player.hasPermission("yapgames.admin")) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage("§cYou cannot run commands during a match.");
    }
}
