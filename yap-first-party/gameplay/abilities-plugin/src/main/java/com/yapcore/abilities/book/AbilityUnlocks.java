package com.yapcore.abilities.book;

import com.yapcore.abilities.AbilityDefinition;
import com.yapcore.mmo.SkillProgress;
import com.yapcore.mmo.SkillServices;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AbilityUnlocks {

    private AbilityUnlocks() {
    }

    public static boolean isUnlocked(Player player, AbilityDefinition ability, Collection<SkillProgress> skills) {
        if (player != null && (player.hasPermission("yapabilities.admin")
                || player.hasPermission("yapabilities.bypass.lock"))) {
            return true;
        }
        if (ability.minLevels().isEmpty()) {
            return true;
        }
        if (skills == null) {
            return SkillServices.find().isEmpty();
        }
        for (Map.Entry<String, Integer> entry : ability.minLevels().entrySet()) {
            int required = entry.getValue();
            int have = levelFor(skills, entry.getKey());
            if (have < required) {
                return false;
            }
        }
        return true;
    }

    public static int levelFor(Collection<SkillProgress> skills, String skillId) {
        if (skills == null) {
            return 99;
        }
        String id = skillId.toLowerCase(Locale.ROOT);
        for (SkillProgress progress : skills) {
            if (progress.skillId().id().equalsIgnoreCase(id)) {
                return progress.level();
            }
        }
        return 1;
    }

    public static String requirementsText(AbilityDefinition ability, Collection<SkillProgress> skills) {
        if (ability.minLevels().isEmpty()) {
            return "§aUnlocked";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : ability.minLevels().entrySet()) {
            if (!sb.isEmpty()) {
                sb.append(" §8· ");
            }
            int have = levelFor(skills, entry.getKey());
            int need = entry.getValue();
            if (have >= need) {
                sb.append("§a").append(capitalize(entry.getKey())).append(' ').append(need);
            } else {
                sb.append("§c").append(capitalize(entry.getKey())).append(' ').append(need)
                        .append(" §7(").append(have).append(')');
            }
        }
        return sb.toString();
    }

    public static List<AbilityDefinition> sorted(
            Collection<AbilityDefinition> all,
            com.yapcore.abilities.AbilityCategory category,
            boolean showLocked,
            Player player,
            Collection<SkillProgress> skills
    ) {
        List<AbilityDefinition> out = new ArrayList<>();
        for (AbilityDefinition def : all) {
            if (category != null && def.category() != category) {
                continue;
            }
            boolean unlocked = isUnlocked(player, def, skills);
            if (unlocked || showLocked) {
                out.add(def);
            }
        }
        out.sort(Comparator.comparing(AbilityDefinition::displayName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private static String capitalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String lower = raw.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
