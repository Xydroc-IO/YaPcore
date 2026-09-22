package com.yapcore.conquest.listener;

import com.yapcore.conquest.ConquestConfig;
import com.yapcore.conquest.service.ConquestServiceImpl;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Grants / strips flight in own conquest territory when {@code fly.enabled}. */
public final class ConquestFlyListener implements Listener {

    private final ConquestConfig config;
    private final ConquestServiceImpl conquest;
    /** Players who received fly from conquest (so we do not strip Essentials /fly). */
    private final Set<UUID> conquestGranted = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Boolean> lastOwn = new ConcurrentHashMap<>();

    public ConquestFlyListener(ConquestConfig config, ConquestServiceImpl conquest) {
        this.config = config;
        this.conquest = conquest;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!config.flyEnabled()) {
            return;
        }
        if (event.getTo() == null) {
            return;
        }
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()
                && event.getFrom().getWorld() == event.getTo().getWorld()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (player.hasPermission("yapconquest.admin")) {
            return;
        }
        boolean eligible = conquest.isOwnTerritory(player, event.getTo());
        if (!eligible && !config.flyOnlyOwnTerritory()) {
            eligible = isAllyTerritory(player, event.getTo());
        }
        Boolean prev = lastOwn.put(player.getUniqueId(), eligible);
        if (prev != null && prev == eligible) {
            if (eligible && config.combatTagBlockFly() && conquest.isCombatTagged(player.getUniqueId())) {
                stripConquestFly(player);
            }
            return;
        }
        if (eligible) {
            if (config.combatTagBlockFly() && conquest.isCombatTagged(player.getUniqueId())) {
                stripConquestFly(player);
                return;
            }
            if (!player.getAllowFlight()) {
                player.setAllowFlight(true);
                conquestGranted.add(player.getUniqueId());
                player.sendMessage("§aConquest fly enabled in your territory.");
            }
        } else if (config.flyDisableOnLeave()) {
            stripConquestFly(player);
        }
    }

    private boolean isAllyTerritory(Player player, org.bukkit.Location location) {
        var land = conquest.chunkAt(location);
        if (land.isEmpty()) {
            return false;
        }
        return com.yapcore.factions.FactionServices.find().map(fs -> {
            var member = fs.member(player.getUniqueId());
            if (member.isEmpty()) {
                return false;
            }
            return fs.relationBetween(member.get().factionId(), land.get().factionId())
                    == com.yapcore.factions.FactionRelation.ALLY;
        }).orElse(false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        lastOwn.remove(id);
        conquestGranted.remove(id);
    }

    public void stripConquestFly(Player player) {
        if (!conquestGranted.remove(player.getUniqueId())) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        player.setFlying(false);
        player.setAllowFlight(false);
    }

    public void clearAll() {
        conquestGranted.clear();
        lastOwn.clear();
    }
}
