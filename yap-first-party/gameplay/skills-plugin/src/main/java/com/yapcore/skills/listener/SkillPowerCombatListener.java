package com.yapcore.skills.listener;

import com.yapcore.mmo.SkillDefinition;
import com.yapcore.skills.SkillsPlugin;
import com.yapcore.skills.power.SkillHitContext;
import com.yapcore.skills.power.SkillPowerBlocks;
import com.yapcore.skills.power.SkillPowerMath;
import com.yapcore.skills.service.SkillServiceImpl;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Strength power on the victim's region thread. Only the damage number is changed.
 * The attacker's entity is not touched. XP keeps the pre-bonus hit via {@link SkillHitContext}.
 * Registered after {@link CombatSkillListener} so the MONITOR clear runs last.
 */
public final class SkillPowerCombatListener implements Listener {

    private final SkillsPlugin plugin;

    public SkillPowerCombatListener(SkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!plugin.power().enabled()) {
            return;
        }
        Player attacker = attacker(event);
        if (attacker == null || !(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        SkillServiceImpl skills = plugin.skillService();
        if (skills == null) {
            return;
        }
        SkillDefinition strength = skills.definition(SkillPowerBlocks.STRENGTH).orElse(null);
        if (strength == null || !strength.enabled() || strength.combatDealt() == null) {
            return;
        }
        int level = 1;
        if (plugin.levels().loaded(attacker.getUniqueId())) {
            level = plugin.levels().level(attacker.getUniqueId(), strength.id());
        }
        double multiplier = SkillPowerMath.damageMultiplier(
                level, skills.xpTable().maxLevel(), plugin.power().damageBonusAtMax());
        SkillHitContext.setTrainingDamage(event.getFinalDamage());
        if (multiplier <= 1.0000001) {
            return;
        }
        double base = event.getDamage();
        if (base <= 0.0) {
            return;
        }
        event.setDamage(base * multiplier);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void clearContext(EntityDamageByEntityEvent event) {
        SkillHitContext.clear();
    }

    private static Player attacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }
}
