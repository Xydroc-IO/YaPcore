package com.yapcore.skills.power;

import java.util.List;
import java.util.Locale;

/** Player-facing power lines. Same text for the menu and the level-up chat. */
public final class SkillPowerText {

    private SkillPowerText() {
    }

    public static List<String> lines(String skillId, int level, int maxLevel, SkillPowerSettings settings) {
        if (settings == null || !settings.enabled() || skillId == null || skillId.isBlank()) {
            return List.of();
        }
        String id = skillId.toLowerCase(Locale.ROOT);
        if ("mining".equals(id) || "woodcutting".equals(id)) {
            double speed = SkillPowerMath.breakSpeed(level, maxLevel, settings.breakSpeedBonusAtMax());
            double extra = SkillPowerMath.expectedExtra(level, maxLevel, settings.extraDropsAtMax());
            java.util.ArrayList<String> out = new java.util.ArrayList<>();
            out.add(speedLine(speed));
            out.add(extraLine(extra));
            boolean max = SkillPowerMath.atMax(level, maxLevel);
            if ("mining".equals(id)) {
                out.add(max ? "Super Breaker: sneak + right-click air" : "Super Breaker: unlocks at max");
            } else {
                out.add(max ? "Tree Feller: sneak + right-click air" : "Tree Feller: unlocks at max");
            }
            return List.copyOf(out);
        }
        if ("strength".equals(id)) {
            double damage = SkillPowerMath.damageMultiplier(level, maxLevel, settings.damageBonusAtMax());
            return List.of(String.format(Locale.ROOT, "Hit damage: %.2fx", damage));
        }
        if ("marathon".equals(id)) {
            double speed = SkillPowerMath.moveSpeed(level, maxLevel, settings.movementSpeedBonusAtMax());
            return List.of(String.format(Locale.ROOT, "Walk speed: %.2fx", speed));
        }
        if ("builder".equals(id)) {
            double reach = SkillPowerMath.placeReach(level, maxLevel, settings.placeReachBonusAtMax());
            double keep = SkillPowerMath.keepBlockChance(level, maxLevel, settings.keepBlockChanceAtMax());
            return List.of(reachLine(reach), keepLine(keep));
        }
        if ("herbalism".equals(id)) {
            return List.of(SkillPowerMath.atMax(level, maxLevel)
                    ? "Green Terra: harvest + replant"
                    : "Green Terra: unlocks at max");
        }
        if ("excavation".equals(id)) {
            return List.of(SkillPowerMath.atMax(level, maxLevel)
                    ? "Rare loot: clay, glowstone, diamonds"
                    : "Rare dig loot: unlocks at max");
        }
        if ("alchemy".equals(id)) {
            double speed = SkillPowerMath.brewSpeed(level, maxLevel, settings.brewSpeedBonusAtMax());
            return List.of(String.format(Locale.ROOT, "Brew speed: %.2fx", speed));
        }
        if ("health".equals(id)) {
            double extra = SkillPowerMath.extraHearts(level, maxLevel, settings.extraHeartsAtMax());
            String hearts = String.format(Locale.ROOT, "Max health: +%.1f hearts", extra / 2.0);
            if (SkillPowerMath.atMax(level, maxLevel)) {
                return List.of(hearts, "Regen: in combat");
            }
            return List.of(hearts, "Regen: unlocks at max");
        }
        return List.of();
    }

    /** Leading section colors, or empty when this skill has no power curve. */
    public static String levelUpDetail(String skillId, int level, int maxLevel, SkillPowerSettings settings) {
        List<String> lines = lines(skillId, level, maxLevel, settings);
        if (lines.isEmpty()) {
            return "";
        }
        return " §7(§f" + String.join("§7, §f", lines) + "§7)";
    }

    private static String speedLine(double speed) {
        return String.format(Locale.ROOT, "Break speed: %.2fx", speed);
    }

    private static String reachLine(double extraBlocks) {
        return String.format(Locale.ROOT, "Place reach: +%.2f", extraBlocks);
    }

    static String keepLine(double chance) {
        if (chance <= 1.0e-7) {
            return "Keep block: none";
        }
        return String.format(Locale.ROOT, "Keep block: %.0f%%", chance * 100.0);
    }

    static String extraLine(double extra) {
        if (extra <= 1.0e-7) {
            return "Extra drops: none";
        }
        int whole = (int) Math.floor(extra + 1.0e-9);
        double fraction = extra - whole;
        if (fraction < 1.0e-7) {
            return "Extra drops: +" + whole;
        }
        if (whole <= 0) {
            return String.format(Locale.ROOT, "Extra drops: %.0f%% chance of +1", fraction * 100.0);
        }
        return String.format(Locale.ROOT, "Extra drops: +%d, %.0f%% chance of another", whole, fraction * 100.0);
    }
}
