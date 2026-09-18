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
            return List.of(speedLine(speed), extraLine(extra));
        }
        if ("strength".equals(id)) {
            double damage = SkillPowerMath.damageMultiplier(level, maxLevel, settings.damageBonusAtMax());
            return List.of(String.format(Locale.ROOT, "Hit damage: %.2fx", damage));
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
