package com.yapcore.games.listener;

import com.yapcore.games.match.Match;
import com.yapcore.games.match.MatchManager;
import com.yapcore.sched.YapSched;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public final class ArenaBoundaryListener implements Listener {

    private final MatchManager matches;
    private final org.bukkit.plugin.java.JavaPlugin plugin;

    public ArenaBoundaryListener(org.bukkit.plugin.java.JavaPlugin plugin, MatchManager matches) {
        this.plugin = plugin;
        this.matches = matches;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        Player player = event.getPlayer();
        Match match = matches.matchOf(player.getUniqueId()).orElse(null);
        if (match == null || !matches.isInActiveMatch(player.getUniqueId())) {
            return;
        }
        if (match.arena().contains(event.getTo())) {
            return;
        }
        if (match.isSpectator(player.getUniqueId())) {
            return;
        }
        event.setTo(event.getFrom());
        YapSched.entity(plugin, player, () ->
                player.sendActionBar(net.kyori.adventure.text.Component.text("§cStay inside the arena!")));
    }
}
