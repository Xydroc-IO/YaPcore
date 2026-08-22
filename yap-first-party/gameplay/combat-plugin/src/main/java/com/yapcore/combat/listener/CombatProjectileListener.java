package com.yapcore.combat.listener;

import com.yapcore.combat.CombatConfig;
import com.yapcore.combat.formula.CombatAttackGate;
import com.yapcore.combat.projectile.CombatProjectileKeys;
import com.yapcore.combat.projectile.ProjectilePhysics;
import com.yapcore.combat.service.CombatHitPipeline;
import com.yapcore.combat.service.CombatServiceImpl;
import com.yapcore.combat.status.StatusEffectService;
import com.yapcore.mmo.CombatStyle;
import com.yapcore.sched.YapSched;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class CombatProjectileListener implements Listener {

    private final JavaPlugin plugin;
    private final CombatConfig config;
    private final CombatServiceImpl combat;
    private final CombatHitPipeline pipeline;
    private final CombatProjectileKeys keys;

    public CombatProjectileListener(
            JavaPlugin plugin,
            CombatConfig config,
            CombatServiceImpl combat,
            CombatHitPipeline pipeline,
            CombatProjectileKeys keys) {
        this.plugin = plugin;
        this.config = config;
        this.combat = combat;
        this.pipeline = pipeline;
        this.keys = keys;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBowShoot(EntityShootBowEvent event) {
        if (!config.enabled() || !config.projectiles().enabled()) {
            return;
        }
        if (!(event.getEntity() instanceof Player shooter)) {
            return;
        }
        Entity projectile = event.getProjectile();
        if (!(projectile instanceof Projectile proj)) {
            return;
        }
        int pierce = ProjectilePhysics.pierceBonus(combat.stats(shooter).ranged(), config.projectiles());
        YapSched.entity(plugin, shooter, () ->
                ProjectilePhysics.tagAndLaunch(proj, shooter, event.getForce(), pierce, keys, config.projectiles()));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!config.enabled() || !config.projectiles().enabled()) {
            return;
        }
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player shooter)) {
            return;
        }
        if (ProjectilePhysics.isManaged(projectile, keys)) {
            return;
        }
        int pierce = ProjectilePhysics.pierceBonus(combat.stats(shooter).ranged(), config.projectiles());
        YapSched.entity(plugin, shooter, () ->
                ProjectilePhysics.tagAndLaunch(projectile, shooter, 1.0f, pierce, keys, config.projectiles()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!config.enabled() || !config.projectiles().enabled()) {
            return;
        }
        Projectile projectile = event.getEntity();
        if (!ProjectilePhysics.isManaged(projectile, keys)) {
            return;
        }
        if (event.getHitBlock() != null) {
            return;
        }
        Entity hit = event.getHitEntity();
        if (!(hit instanceof LivingEntity victim)) {
            return;
        }
        Player shooter = ProjectilePhysics.resolveShooter(projectile, keys);
        if (shooter == null) {
            return;
        }
        event.setCancelled(true);
        boolean headshot = ProjectilePhysics.isHeadshot(projectile, victim);
        double dropOff = ProjectilePhysics.dropOffMultiplier(projectile, keys, config.projectiles());
        pipeline.beginPlayerAttack(
                shooter,
                victim,
                CombatStyle.RANGED,
                new CombatHitPipeline.HitModifiers(headshot, dropOff));
        int pierce = ProjectilePhysics.readPierce(projectile, keys);
        if (pierce > 0 && projectile instanceof AbstractArrow arrow) {
            ProjectilePhysics.decrementPierce(projectile, keys);
            arrow.setPickupStatus(AbstractArrow.PickupStatus.CREATIVE_ONLY);
            return;
        }
        YapSched.entity(plugin, projectile, projectile::remove);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileDamage(EntityDamageByEntityEvent event) {
        if (!config.enabled() || !config.projectiles().enabled()) {
            return;
        }
        if (event.getDamager() instanceof Projectile projectile
                && ProjectilePhysics.isManaged(projectile, keys)) {
            event.setCancelled(true);
        }
    }
}
