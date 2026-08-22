package com.yapcore.combat.formula;

import com.yapcore.combat.CombatConfig;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/** Knockback and hit feedback from successful combat hits. */
public final class CombatPhysics {

    private CombatPhysics() {
    }

    public static void applyKnockback(
            LivingEntity victim,
            LivingEntity source,
            DamageCalculator.Result result,
            CombatConfig.PhysicsConfig physics) {
        if (!physics.enabled() || !result.hit() || result.damage() <= 0 || result.maxHit() <= 0) {
            return;
        }
        Vector push = victim.getLocation().toVector().subtract(source.getLocation().toVector());
        if (push.lengthSquared() < 1.0E-4) {
            push = source.getLocation().getDirection().clone();
        }
        push.normalize();
        double ratio = Math.min(1.0, (double) result.damage() / result.maxHit());
        double horizontal = physics.baseKnockback() + ratio * physics.damageScale();
        push.multiply(horizontal);
        push.setY(Math.max(physics.verticalBoost(), push.getY() + physics.verticalBoost()));
        victim.setVelocity(victim.getVelocity().add(push));
    }
}
