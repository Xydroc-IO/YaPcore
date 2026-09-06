package com.yapcore.conquest.listener;

import com.yapcore.conquest.ConquestConfig;
import com.yapcore.conquest.service.ConquestServiceImpl;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.projectiles.ProjectileSource;

/** Combat tag on PvP hit; blocks teleport (and fly via ConquestFlyListener) while tagged. */
public final class ConquestCombatListener implements Listener {

    private final ConquestConfig config;
    private final ConquestServiceImpl conquest;
    private final ConquestFlyListener flyListener;

    public ConquestCombatListener(
            ConquestConfig config, ConquestServiceImpl conquest, ConquestFlyListener flyListener) {
        this.config = config;
        this.conquest = conquest;
        this.flyListener = flyListener;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!config.combatTagEnabled()) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttacker(event);
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        // Only tag when PvP was allowed (or no conquest override)
        var allowed = conquest.evaluatePvp(attacker, victim, victim.getLocation());
        if (allowed.isPresent() && !allowed.get()) {
            return;
        }
        tagPlayer(attacker);
        tagPlayer(victim);
        if (config.combatTagBlockFly()) {
            flyListener.stripConquestFly(attacker);
            flyListener.stripConquestFly(victim);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!config.combatTagEnabled() || !config.combatTagBlockTeleport()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.hasPermission("yapconquest.admin")) {
            return;
        }
        if (!conquest.isCombatTagged(player.getUniqueId())) {
            return;
        }
        String cause = event.getCause().name();
        if (cause.equals("ENDER_PEARL")
                || cause.equals("CHORUS_FRUIT")
                || cause.equals("COMMAND")
                || cause.equals("PLUGIN")
                || cause.equals("SPECTATE")) {
            event.setCancelled(true);
            player.sendMessage("§cYou cannot teleport while combat tagged.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        conquest.combatTags().clear(event.getPlayer().getUniqueId());
    }

    private void tagPlayer(Player player) {
        boolean was = conquest.isCombatTagged(player.getUniqueId());
        conquest.tagCombat(player.getUniqueId());
        if (!was) {
            player.sendMessage(config.combatTagMessage()
                    .replace("%seconds%", Integer.toString(config.combatTagSeconds())));
        }
    }

    private static Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource src = projectile.getShooter();
            if (src instanceof Player player) {
                return player;
            }
        }
        return null;
    }
}
