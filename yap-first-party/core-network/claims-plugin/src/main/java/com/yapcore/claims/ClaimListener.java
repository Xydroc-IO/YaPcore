package com.yapcore.claims;

import org.bukkit.WeatherType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClaimListener implements Listener {

    private final ClaimService claims;
    private final Set<UUID> clearWeatherForced = ConcurrentHashMap.newKeySet();

    public ClaimListener(JavaPlugin plugin, ClaimService claims) {
        this.claims = claims;
        var pm = plugin.getServer().getPluginManager();
        pm.registerEvents(new ClaimListenerBuild(claims), plugin);
        pm.registerEvents(new ClaimListenerCombat(claims), plugin);
        pm.registerEvents(new ClaimListenerExplosion(claims), plugin);
        pm.registerEvents(new ClaimListenerPortal(claims), plugin);
        pm.registerEvents(new ClaimListenerMob(claims), plugin);
        pm.registerEvents(new ClaimListenerInteract(plugin, claims), plugin);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(org.bukkit.event.player.PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        var fromClaim = claims.getAt(event.getFrom());
        var toClaim = claims.getAt(event.getTo());
        if (fromClaim.map(Claim::id).equals(toClaim.map(Claim::id))) {
            return;
        }
        if (!claims.canEnter(event.getPlayer(), event.getTo())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cEntry denied in this claim.");
            return;
        }
        Player player = event.getPlayer();
        fromClaim.flatMap(c -> claims.message(c.id(), ClaimMessageKind.FAREWELL))
                .ifPresent(player::sendMessage);
        toClaim.flatMap(c -> claims.message(c.id(), ClaimMessageKind.GREETING))
                .ifPresent(player::sendMessage);
        applyClaimWeather(player, event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        clearWeatherForced.remove(event.getPlayer().getUniqueId());
        claims.clearBorderView(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!claims.canDropItems(event.getPlayer(), event.getPlayer().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cClaimed land — item drop denied.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!claims.canPickupItems(player, player.getLocation())) {
            event.setCancelled(true);
            player.sendMessage("§cClaimed land — item pickup denied.");
        }
    }

    private void applyClaimWeather(Player player, org.bukkit.Location location) {
        if (com.yapcore.regions.RegionServices.find().flatMap(s -> s.at(location)).isPresent()) {
            return;
        }
        if (claims.forcesClearWeather(location)) {
            player.setPlayerWeather(WeatherType.CLEAR);
            clearWeatherForced.add(player.getUniqueId());
            return;
        }
        if (clearWeatherForced.remove(player.getUniqueId())) {
            player.resetPlayerWeather();
        }
    }
}
