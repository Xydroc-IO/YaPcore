package com.yapcore.combat.listener;

import com.yapcore.combat.CombatPlugin;
import com.yapcore.combat.integration.ClaimIntegration;
import com.yapcore.combat.integration.CombatPvpGate;
import com.yapcore.combat.projectile.ProjectilePhysics;
import com.yapcore.combat.service.CombatHitPipeline;
import com.yapcore.combat.service.CombatXpAwarder;
import com.yapcore.mmo.CombatStyle;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

public final class CombatDamageListener implements Listener {

    private final CombatPlugin plugin;

    public CombatDamageListener(CombatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!plugin.combatConfig().enabled()) {
            return;
        }
        if (plugin.entityDamager() != null && plugin.entityDamager().isBypassing(event.getEntity())) {
            // Custom combat already decided damage — let vanilla apply it.
            return;
        }
        if (event.getDamager() instanceof Projectile projectile
                && plugin.projectileKeys() != null
                && ProjectilePhysics.isManaged(projectile, plugin.projectileKeys())
                && plugin.combatConfig().projectiles().enabled()) {
            event.setCancelled(true);
            return;
        }
        Player attacker = resolveAttacker(event);
        if (attacker == null) {
            if (event.getEntity() instanceof Player victim) {
                handleMobVsPlayer(event, victim);
            }
            return;
        }

        if (event.getEntity() instanceof Player victimPlayer) {
            if (!CombatPvpGate.isPlayerVsPlayerAllowed(plugin.combatConfig(), attacker, victimPlayer)) {
                event.setCancelled(true);
                if (!plugin.combatConfig().pvp()) {
                    attacker.sendMessage("§cPvP is disabled.");
                } else {
                    attacker.sendMessage("§cPvP is not allowed here.");
                }
                return;
            }
            handlePlayerVsPlayer(event, attacker, victimPlayer);
            return;
        }

        if (event.getEntity() instanceof LivingEntity mob) {
            handlePlayerVsMob(event, attacker, mob);
        }
    }

    private void handlePlayerVsMob(EntityDamageByEntityEvent event, Player attacker, LivingEntity mob) {
        event.setCancelled(true);
        if (event.getDamager() instanceof Projectile) {
            return;
        }
        plugin.hitPipeline().beginPlayerAttack(
                attacker, mob, CombatStyle.MELEE, CombatHitPipeline.HitModifiers.none());
    }

    private void handleMobVsPlayer(EntityDamageByEntityEvent event, Player victim) {
        if (!ClaimIntegration.isMobDamageAllowed(victim) || victim.isInvulnerable()) {
            event.setCancelled(true);
            return;
        }
        LivingEntity mob = event.getDamager() instanceof LivingEntity le ? le : null;
        if (mob == null) {
            return;
        }
        event.setCancelled(true);
        plugin.hitPipeline().beginMobAttack(mob, victim);
    }

    private void handlePlayerVsPlayer(
            EntityDamageByEntityEvent event,
            Player attacker,
            Player victim) {
        event.setCancelled(true);
        if (event.getDamager() instanceof Projectile) {
            return;
        }
        plugin.hitPipeline().beginPlayerAttack(
                attacker, victim, CombatStyle.MELEE, CombatHitPipeline.HitModifiers.none());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        if (!plugin.combatConfig().enabled()) {
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer == null || event.getEntity() instanceof Player) {
            return;
        }
        CombatStyle style = combatStyle(killer, null);
        plugin.xpAwarder().awardKill(killer.getUniqueId(), style);
        plugin.comboService().reset(killer);
    }

    private static Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private static CombatStyle combatStyle(Player attacker, EntityDamageByEntityEvent event) {
        if (event != null && event.getDamager() instanceof Projectile) {
            return CombatStyle.RANGED;
        }
        ItemStack hand = attacker.getInventory().getItemInMainHand();
        Material type = hand.getType();
        if (type == Material.BOW || type == Material.CROSSBOW) {
            return CombatStyle.RANGED;
        }
        return CombatStyle.MELEE;
    }
}
