package com.yapcore.factions.listener;

import com.yapcore.factions.Faction;
import com.yapcore.factions.FactionsConfig;
import com.yapcore.factions.service.FactionServiceImpl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FactionTerritoryListener implements Listener {

    private final FactionsConfig config;
    private final FactionServiceImpl factions;
    private final Map<UUID, Long> lastTerritory = new ConcurrentHashMap<>();

    public FactionTerritoryListener(FactionsConfig config, FactionServiceImpl factions) {
        this.config = config;
        this.factions = factions;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        Player player = event.getPlayer();
        Optional<Long> next = factions.factionAt(event.getTo());
        Long prev = lastTerritory.get(player.getUniqueId());
        long nextId = next.orElse(-1L);
        if (prev != null && prev == nextId) {
            return;
        }
        lastTerritory.put(player.getUniqueId(), nextId);
        if (next.isPresent()) {
            Faction faction = factions.getFaction(next.get()).orElse(null);
            if (faction != null) {
                player.sendMessage(config.territoryEnterMessage().replace("%faction%", faction.name()));
            }
        } else if (prev != null && prev > 0) {
            Faction faction = factions.getFaction(prev).orElse(null);
            if (faction != null) {
                player.sendMessage(config.territoryLeaveMessage().replace("%faction%", faction.name()));
            }
        }
    }
}
