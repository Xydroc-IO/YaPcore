package com.yapcore.skills.listener;

import com.yapcore.mmo.SkillDefinition;
import com.yapcore.mmo.SkillId;
import com.yapcore.mmo.XpSource;
import com.yapcore.sched.YapSched;
import com.yapcore.skills.SkillsPlugin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Combat skill XP (M1 stub — vanilla damage values, no custom combat formulas until M2).
 * Attack/strength split 50/50; hitpoints receives configured ratio of combined combat XP.
 */
public final class CombatSkillListener implements Listener {

    private static final SkillId ATTACK = SkillId.of("attack");
    private static final SkillId STRENGTH = SkillId.of("strength");
    private static final SkillId DEFENCE = SkillId.of("defence");
    private static final SkillId HITPOINTS = SkillId.of("hitpoints");

    private final SkillsPlugin plugin;

    public CombatSkillListener(SkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageDealt(EntityDamageByEntityEvent event) {
        Player player = resolveAttacker(event);
        if (player == null) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target) || target instanceof Player) {
            return;
        }
        double damage = event.getFinalDamage();
        if (damage <= 0) {
            return;
        }
        SkillDefinition attackDef = plugin.skillService().definition(ATTACK).orElse(null);
        SkillDefinition strengthDef = plugin.skillService().definition(STRENGTH).orElse(null);
        SkillDefinition hpDef = plugin.skillService().definition(HITPOINTS).orElse(null);
        if (attackDef == null && strengthDef == null) {
            return;
        }
        double attackXp = xpForDealt(attackDef, damage);
        double strengthXp = xpForDealt(strengthDef, damage);
        double combatXp = attackXp + strengthXp;
        double hpXp = hpXp(hpDef, combatXp);
        grantAsync(player, attackDef, ATTACK, attackXp);
        grantAsync(player, strengthDef, STRENGTH, strengthXp);
        grantAsync(player, hpDef, HITPOINTS, hpXp);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageTaken(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.CUSTOM) {
            return;
        }
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            if (byEntity.getDamager() instanceof Player) {
                return;
            }
        }
        double damage = event.getFinalDamage();
        if (damage <= 0) {
            return;
        }
        SkillDefinition def = plugin.skillService().definition(DEFENCE).orElse(null);
        if (def == null || !def.enabled() || def.combatTaken() == null) {
            return;
        }
        double xp = damage * def.combatTaken().xpPerDamage();
        grantAsync(player, def, DEFENCE, xp);
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

    private static double xpForDealt(SkillDefinition def, double damage) {
        if (def == null || !def.enabled() || def.combatDealt() == null) {
            return 0;
        }
        return damage * def.combatDealt().xpPerDamage() * def.combatDealt().share();
    }

    private static double hpXp(SkillDefinition def, double combatXp) {
        if (def == null || !def.enabled() || def.hitpointsRatio() == null || combatXp <= 0) {
            return 0;
        }
        return combatXp * def.hitpointsRatio().ratio();
    }

    private void grantAsync(Player player, SkillDefinition def, SkillId skillId, double xp) {
        if (def == null || !def.enabled() || xp <= 0) {
            return;
        }
        var skills = plugin.skillService();
        YapSched.async(plugin, () -> skills.addXp(player.getUniqueId(), skillId, xp, XpSource.ACTION)
                .thenAccept(updated -> YapSched.entity(plugin, player, () -> {
                    if (player.isOnline()) {
                        skills.showXpGain(player, skillId, xp);
                    }
                })));
    }
}
