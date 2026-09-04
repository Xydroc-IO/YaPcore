package com.yapcore.knobs;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;

import java.util.Locale;
import java.util.Map;

/** Applies encyclopedia attribute overrides via Paper Attribute API. */
public final class AttributeApplier {

    private AttributeApplier() {
    }

    public static void apply(LivingEntity entity, KnobsConfig.MobKnobs knobs) {
        if (knobs == null || knobs.attributes().isEmpty()) {
            return;
        }
        for (Map.Entry<String, Double> e : knobs.attributes().entrySet()) {
            Attribute attr = resolve(e.getKey());
            if (attr == null) {
                continue;
            }
            double value = e.getValue();
            if (value <= 0 && attr != Attribute.SCALE) {
                continue;
            }
            AttributeInstance inst = entity.getAttribute(attr);
            if (inst == null) {
                continue;
            }
            inst.setBaseValue(value);
            if (attr == Attribute.MAX_HEALTH) {
                entity.setHealth(Math.min(entity.getHealth(), value));
            }
        }
    }

    static Attribute resolve(String key) {
        if (key == null) {
            return null;
        }
        String k = key.toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (k) {
            case "max_health", "generic_max_health" -> Attribute.MAX_HEALTH;
            case "scale" -> Attribute.SCALE;
            case "movement_speed", "generic_movement_speed" -> Attribute.MOVEMENT_SPEED;
            case "attack_damage", "generic_attack_damage" -> Attribute.ATTACK_DAMAGE;
            case "attack_knockback", "generic_attack_knockback" -> Attribute.ATTACK_KNOCKBACK;
            case "armor", "generic_armor" -> Attribute.ARMOR;
            case "armor_toughness", "generic_armor_toughness" -> Attribute.ARMOR_TOUGHNESS;
            case "follow_range", "generic_follow_range" -> Attribute.FOLLOW_RANGE;
            case "knockback_resistance", "generic_knockback_resistance" -> Attribute.KNOCKBACK_RESISTANCE;
            case "flying_speed", "generic_flying_speed" -> Attribute.FLYING_SPEED;
            case "spawn_reinforcements", "zombie_spawn_reinforcements" -> Attribute.SPAWN_REINFORCEMENTS;
            case "attack_speed", "generic_attack_speed" -> Attribute.ATTACK_SPEED;
            case "luck", "generic_luck" -> Attribute.LUCK;
            case "jump_strength", "generic_jump_strength", "horse_jump_strength" -> Attribute.JUMP_STRENGTH;
            case "step_height" -> Attribute.STEP_HEIGHT;
            case "water_movement_efficiency" -> Attribute.WATER_MOVEMENT_EFFICIENCY;
            case "burning_time" -> Attribute.BURNING_TIME;
            case "explosion_knockback_resistance" -> Attribute.EXPLOSION_KNOCKBACK_RESISTANCE;
            case "movement_efficiency" -> Attribute.MOVEMENT_EFFICIENCY;
            case "oxygen_bonus" -> Attribute.OXYGEN_BONUS;
            case "safe_fall_distance" -> Attribute.SAFE_FALL_DISTANCE;
            case "fall_damage_multiplier" -> Attribute.FALL_DAMAGE_MULTIPLIER;
            case "gravity" -> Attribute.GRAVITY;
            default -> null;
        };
    }

    public static int supportedAttributeKeys() {
        return 22;
    }
}
