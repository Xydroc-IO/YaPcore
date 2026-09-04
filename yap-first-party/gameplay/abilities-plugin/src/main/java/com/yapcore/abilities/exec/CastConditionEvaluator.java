package com.yapcore.abilities.exec;

import com.yapcore.abilities.AbilityDefinition;
import com.yapcore.abilities.CastCondition;
import com.yapcore.abilities.StatusEffectServices;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Optional;

public final class CastConditionEvaluator {

    private CastConditionEvaluator() {
    }

    public static boolean passes(Player caster, AbilityDefinition ability) {
        for (CastCondition condition : ability.conditions()) {
            if (!check(caster, condition)) {
                return false;
            }
        }
        return true;
    }

    public static Optional<CastCondition> firstFailure(Player caster, AbilityDefinition ability) {
        for (CastCondition condition : ability.conditions()) {
            if (!check(caster, condition)) {
                return Optional.of(condition);
            }
        }
        return Optional.empty();
    }

    public static String failureMessage(CastCondition condition) {
        return switch (condition.kind()) {
            case HAS_STATUS -> "Requires status: " + condition.param("id", "?");
            case LACKS_STATUS -> "Cannot cast while affected by " + condition.param("id", "?");
            case MIN_HP_PERCENT -> "Requires at least " + condition.intParam("percent", 50) + "% HP";
            case MAX_HP_PERCENT -> "Requires at most " + condition.intParam("percent", 50) + "% HP";
            case REQUIRES_MAINHAND -> "Requires " + condition.param("material", "item") + " in main hand";
            case OFFHAND_EMPTY -> "Off-hand must be empty";
            case ON_GROUND -> "Must be on the ground";
            case IN_AIR -> "Must be in the air";
        };
    }

    private static boolean check(Player caster, CastCondition condition) {
        return switch (condition.kind()) {
            case HAS_STATUS -> hasStatus(caster, condition.param("id", ""));
            case LACKS_STATUS -> !hasStatus(caster, condition.param("id", ""));
            case MIN_HP_PERCENT -> hpPercent(caster) >= condition.intParam("percent", 50);
            case MAX_HP_PERCENT -> hpPercent(caster) <= condition.intParam("percent", 50);
            case REQUIRES_MAINHAND -> {
                if (!requireItemConditions()) {
                    yield true;
                }
                yield mainHandIs(caster, condition.param("material", ""));
            }
            case OFFHAND_EMPTY -> caster.getInventory().getItemInOffHand().getType().isAir();
            case ON_GROUND -> caster.isOnGround();
            case IN_AIR -> !caster.isOnGround();
        };
    }

    private static boolean hasStatus(Player player, String id) {
        if (id.isBlank()) {
            return false;
        }
        return StatusEffectServices.find()
                .map(s -> s.active(player.getUniqueId()).stream()
                        .anyMatch(a -> a.effectId().equalsIgnoreCase(id)))
                .orElse(false);
    }

    private static double hpPercent(Player player) {
        double max = Math.max(1.0, player.getMaxHealth());
        return (player.getHealth() / max) * 100.0;
    }

    private static boolean mainHandIs(Player player, String materialName) {
        if (materialName.isBlank()) {
            return false;
        }
        Material expected = Material.matchMaterial(materialName.toUpperCase(Locale.ROOT));
        return expected != null && player.getInventory().getItemInMainHand().getType() == expected;
    }

    /** When staff/item costs are disabled, skip main-hand material gates too. */
    private static boolean requireItemConditions() {
        var plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("YaPAbilities");
        if (plugin == null) {
            return false;
        }
        return plugin.getConfig().getBoolean("costs.require-staff", false);
    }
}
