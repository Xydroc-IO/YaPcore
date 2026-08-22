package com.yapcore.games.listener;

import com.yapcore.games.match.MatchManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class MatchListener implements Listener {

    private final MatchManager matches;

    public MatchListener(MatchManager matches) {
        this.matches = matches;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (!matches.isInActiveMatch(victim.getUniqueId())) {
            return;
        }
        event.getDrops().clear();
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        Player killer = victim.getKiller();
        matches.handleDeath(victim, killer);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        matches.handleQuit(event.getPlayer().getUniqueId());
    }
}
