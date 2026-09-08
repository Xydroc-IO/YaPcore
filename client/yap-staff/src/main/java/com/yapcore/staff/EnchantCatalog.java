package com.yapcore.staff;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Curated enchantments for the custom-item create picker. */
public final class EnchantCatalog {

    public record Info(String id, String label, int maxLevel, String group) {
    }

    private static final List<Info> ALL = List.of(
            // Weapon
            new Info("sharpness", "Sharpness", 5, "weapon"),
            new Info("smite", "Smite", 5, "weapon"),
            new Info("bane_of_arthropods", "Bane of Arthropods", 5, "weapon"),
            new Info("knockback", "Knockback", 2, "weapon"),
            new Info("fire_aspect", "Fire Aspect", 2, "weapon"),
            new Info("looting", "Looting", 3, "weapon"),
            new Info("sweeping_edge", "Sweeping Edge", 3, "weapon"),
            // Tool
            new Info("efficiency", "Efficiency", 5, "tool"),
            new Info("fortune", "Fortune", 3, "tool"),
            new Info("silk_touch", "Silk Touch", 1, "tool"),
            // Bow / ranged
            new Info("power", "Power", 5, "ranged"),
            new Info("punch", "Punch", 2, "ranged"),
            new Info("flame", "Flame", 1, "ranged"),
            new Info("infinity", "Infinity", 1, "ranged"),
            new Info("multishot", "Multishot", 1, "ranged"),
            new Info("piercing", "Piercing", 4, "ranged"),
            new Info("quick_charge", "Quick Charge", 3, "ranged"),
            new Info("loyalty", "Loyalty", 3, "ranged"),
            new Info("channeling", "Channeling", 1, "ranged"),
            new Info("riptide", "Riptide", 3, "ranged"),
            new Info("impaling", "Impaling", 5, "ranged"),
            // Armor
            new Info("protection", "Protection", 4, "armor"),
            new Info("projectile_protection", "Projectile Prot", 4, "armor"),
            new Info("fire_protection", "Fire Prot", 4, "armor"),
            new Info("blast_protection", "Blast Prot", 4, "armor"),
            new Info("thorns", "Thorns", 3, "armor"),
            new Info("feather_falling", "Feather Falling", 4, "armor"),
            new Info("respiration", "Respiration", 3, "armor"),
            new Info("aqua_affinity", "Aqua Affinity", 1, "armor"),
            new Info("depth_strider", "Depth Strider", 3, "armor"),
            new Info("soul_speed", "Soul Speed", 3, "armor"),
            new Info("swift_sneak", "Swift Sneak", 3, "armor"),
            // Shared
            new Info("unbreaking", "Unbreaking", 3, "any"),
            new Info("mending", "Mending", 1, "any"),
            new Info("lure", "Lure", 3, "tool"),
            new Info("luck_of_the_sea", "Luck of the Sea", 3, "tool")
    );

    private EnchantCatalog() {
    }

    public static List<Info> all() {
        return ALL;
    }

    /** Filter for item template groups: weapon, tool, gem, prop, armor. */
    public static List<Info> forItemGroup(String itemGroup) {
        String g = itemGroup == null ? "weapon" : itemGroup.toLowerCase(Locale.ROOT);
        List<Info> out = new ArrayList<>();
        for (Info info : ALL) {
            if ("any".equals(info.group())) {
                out.add(info);
                continue;
            }
            if ("weapon".equals(g) && ("weapon".equals(info.group()) || "ranged".equals(info.group()))) {
                out.add(info);
            } else if ("tool".equals(g) && "tool".equals(info.group())) {
                out.add(info);
            } else if (("gem".equals(g) || "prop".equals(g)) && "any".equals(info.group())) {
                // already added via any
            } else if ("armor".equals(g) && "armor".equals(info.group())) {
                out.add(info);
            } else if (("gem".equals(g) || "prop".equals(g)) && !"any".equals(info.group())) {
                // skip combat-only for props/gems unless shared
            }
        }
        // Gems/props: show shared + a few useful combat ones
        if ("gem".equals(g) || "prop".equals(g)) {
            for (Info info : ALL) {
                if ("unbreaking".equals(info.id()) || "mending".equals(info.id())) {
                    if (!out.contains(info)) {
                        out.add(info);
                    }
                }
            }
        }
        if (out.isEmpty()) {
            return ALL;
        }
        return out;
    }

    public static Info get(String id) {
        if (id == null) {
            return null;
        }
        String key = id.toLowerCase(Locale.ROOT);
        for (Info info : ALL) {
            if (info.id().equals(key)) {
                return info;
            }
        }
        return null;
    }

    public static String roman(int level) {
        return switch (Math.max(1, Math.min(10, level))) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            default -> "X";
        };
    }

    public static String labelWithLevel(String id, int level) {
        Info info = get(id);
        String name = info == null ? id : info.label();
        if (level <= 0) {
            return name;
        }
        int max = info == null ? 10 : info.maxLevel();
        if (max <= 1) {
            return name;
        }
        return name + " " + roman(level);
    }
}
