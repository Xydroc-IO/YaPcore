package com.yapcore.leveledmobs.listener;

import com.yapcore.leveledmobs.LeveledMobsConfig;
import com.yapcore.leveledmobs.LeveledMobsPlugin;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

public final class MobCombatListener implements Listener {

    private final LeveledMobsPlugin plugin;

    public MobCombatListener(LeveledMobsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        LeveledMobsConfig cfg = plugin.leveledConfig();
        if (!cfg.enabled()) {
            return;
        }
        LivingEntity mob = leveledMob(event.getEntity());
        LivingEntity attackerLiving = livingDamager(event.getDamager());

        if (mob != null && attackerLiving instanceof Player) {
            int level = plugin.store().getLevel(mob);
            double steps = Math.max(0, level - 1);
            double reduction = Math.min(0.75, steps * cfg.incomingDamageReductionPerLevel());
            event.setDamage(event.getDamage() * (1.0 - reduction));
        }

        LivingEntity attackingMob = leveledMob(attackerLiving);
        if (attackingMob != null && event.getEntity() instanceof Player) {
            int level = plugin.store().getLevel(attackingMob);
            double steps = Math.max(0, level - 1);
            event.setDamage(event.getDamage() * (1.0 + steps * cfg.outgoingDamagePerLevel()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LeveledMobsConfig cfg = plugin.leveledConfig();
        if (!cfg.enabled()) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (!plugin.store().hasLevel(entity)) {
            return;
        }
        int level = plugin.store().getLevel(entity);
        double steps = Math.max(0, level - 1);
        if (cfg.xpPerLevel() > 0 && event.getDroppedExp() > 0) {
            int xp = (int) Math.round(event.getDroppedExp() * (1.0 + steps * cfg.xpPerLevel()));
            event.setDroppedExp(Math.max(0, xp));
        }
        if (cfg.dropQuantityPerLevel() > 0 && !event.getDrops().isEmpty()) {
            double mult = 1.0 + steps * cfg.dropQuantityPerLevel();
            if (mult > 1.01) {
                for (ItemStack drop : event.getDrops()) {
                    if (drop == null || drop.getAmount() <= 0) {
                        continue;
                    }
                    int next = (int) Math.round(drop.getAmount() * mult);
                    drop.setAmount(Math.max(1, Math.min(drop.getMaxStackSize(), next)));
                }
            }
        }
    }

    private LivingEntity leveledMob(Entity entity) {
        if (!(entity instanceof LivingEntity living) || living instanceof Player) {
            return null;
        }
        if (!plugin.store().hasLevel(living)) {
            return null;
        }
        return living;
    }

    private static LivingEntity livingDamager(Entity damager) {
        if (damager instanceof LivingEntity living) {
            return living;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource src = projectile.getShooter();
            if (src instanceof LivingEntity living) {
                return living;
            }
        }
        return null;
    }
}
