package com.yapcore.admin.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Ability catalog for chest create UI — labels, options, item-group suitability. */
public final class AbilityCatalog {

    public record Info(
            String id,
            String label,
            String description,
            String category,
            Set<String> itemGroups,
            boolean needsDamage,
            boolean needsRange,
            boolean needsRadius,
            boolean needsPotion,
            boolean needsPotionPower,
            boolean needsProjectile,
            boolean needsHeal,
            boolean needsBreakVolume) {
    }

    private static final Map<String, Info> BY_ID = new LinkedHashMap<>();

    static {
        add("lightning_dash", "Lightning dash", "Teleport where you look; zap nearby foes",
                "combat", Set.of("weapon"), true, true, false, false, false, false, false, false);
        add("smite_target", "Smite target", "Lightning + damage on the mob you aim at",
                "combat", Set.of("weapon"), true, true, false, false, false, false, false, false);
        add("explode", "Explosion", "Explosion at your feet",
                "combat", Set.of("weapon"), true, false, false, false, false, false, false, false);
        add("fireball", "Fireball", "Shoot a fireball",
                "combat", Set.of("weapon"), false, false, false, false, false, false, false, false);
        add("ground_slam", "Ground stomp", "AoE damage + knockback around you",
                "combat", Set.of("weapon"), true, false, true, false, false, false, false, false);
        add("projectile", "Throw projectile", "Throw snowball / arrow / egg / …",
                "combat", Set.of("weapon", "tool"), false, false, false, false, false, true, false, false);
        add("pull", "Pull mobs", "Yank nearby mobs toward you",
                "combat", Set.of("weapon", "tool"), false, false, true, false, false, false, false, false);
        add("push", "Push mobs", "Knock nearby mobs away",
                "combat", Set.of("weapon"), false, false, true, false, false, false, false, false);
        add("area_effect", "Enemy AoE potion", "Potion on nearby enemies (not you)",
                "combat", Set.of("weapon", "gem"), false, false, true, true, true, false, false, false);

        add("blink", "Blink", "Short teleport where you look",
                "movement", Set.of("weapon", "gem", "other"), false, true, false, false, false, false, false, false);
        add("dash", "Velocity dash", "Boost forward with velocity",
                "movement", Set.of("weapon", "tool"), false, true, false, false, false, false, false, false);
        add("launch", "Launch up", "Fling yourself forward / up",
                "movement", Set.of("weapon", "gem"), false, false, false, false, false, false, false, false);
        add("lightning", "Strike ground", "Lightning at the block you aim at",
                "movement", Set.of("weapon"), false, true, false, false, false, false, false, false);

        add("heal", "Heal self", "Restore hearts",
                "self", Set.of("gem", "other"), false, false, false, false, false, false, true, false);
        add("feed", "Feed self", "Restore hunger",
                "self", Set.of("gem", "other"), false, false, false, false, false, false, false, false);
        add("effect", "Self potion", "Potion on you — pick effect, time, strength",
                "self", Set.of("gem", "weapon", "other"), false, false, false, true, true, false, false, false);
        add("absorb", "Gold hearts", "Temporary yellow absorption hearts",
                "self", Set.of("gem", "weapon", "other"), false, false, false, false, true, false, false, false);
        add("cleanse", "Cleanse", "Clear all potion effects on you",
                "self", Set.of("gem", "other"), false, false, false, false, false, false, false, false);

        add("break_block", "Break blocks", "Reach + blast size + max blocks broken",
                "tool", Set.of("tool"), false, true, false, false, false, false, false, true);
        add("repair", "Repair tool", "Restore held-item durability",
                "tool", Set.of("tool"), false, false, false, false, false, false, false, false);

        add("message", "Chat message", "Send yourself a chat message",
                "utility", Set.of("prop", "other", "gem"), false, false, false, false, false, false, false, false);
        add("sound", "Play sound", "Play a sound at your location",
                "utility", Set.of("prop", "other", "gem"), false, false, false, false, false, false, false, false);
        add("particle", "Particles", "Spawn particles around you",
                "utility", Set.of("prop", "other", "gem"), false, false, false, false, false, false, false, false);
    }

    private AbilityCatalog() {
    }

    private static void add(
            String id, String label, String description, String category, Set<String> groups,
            boolean dmg, boolean range, boolean radius, boolean potion, boolean potionPower,
            boolean proj, boolean heal, boolean breakVol) {
        BY_ID.put(id, new Info(id, label, description, category, Set.copyOf(groups),
                dmg, range, radius, potion, potionPower, proj, heal, breakVol));
    }

    public static Info get(String id) {
        if (id == null) {
            return null;
        }
        return BY_ID.get(id.toLowerCase(Locale.ROOT));
    }

    public static String label(String id) {
        Info info = get(id);
        return info == null ? id : info.label();
    }

    public static String description(String id) {
        Info info = get(id);
        return info == null ? "" : info.description();
    }

    public static String idByLabel(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        String want = label.trim();
        if ("none".equalsIgnoreCase(want)) {
            return "none";
        }
        for (Info info : BY_ID.values()) {
            if (info.label().equalsIgnoreCase(want) || info.id().equalsIgnoreCase(want)) {
                return info.id();
            }
        }
        return null;
    }

    public static boolean anyNeeds(java.util.Collection<String> ids, java.util.function.Predicate<Info> pred) {
        for (String id : ids) {
            Info info = get(id);
            if (info != null && pred.test(info)) {
                return true;
            }
        }
        return false;
    }

    public static Iterable<Info> all() {
        return BY_ID.values();
    }

    public static List<Info> forItemGroup(String itemGroup) {
        String g = itemGroup == null ? "" : itemGroup.toLowerCase(Locale.ROOT);
        if ("key".equals(g)) {
            g = "other";
        }
        List<Info> out = new ArrayList<>();
        for (Info info : BY_ID.values()) {
            if (info.itemGroups().contains(g) || info.itemGroups().contains("all")) {
                out.add(info);
            }
        }
        return out;
    }

    public static String[] categoryOrder() {
        return new String[] {"combat", "movement", "self", "tool", "utility"};
    }

    public static String categoryTitle(String category) {
        return switch (category) {
            case "combat" -> "Combat";
            case "movement" -> "Movement";
            case "self" -> "Self buffs";
            case "tool" -> "Gathering / tools";
            case "utility" -> "Utility";
            default -> category;
        };
    }
}
