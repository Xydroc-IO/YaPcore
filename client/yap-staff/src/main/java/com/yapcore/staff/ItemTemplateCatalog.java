package com.yapcore.staff;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Concrete create-base choices (no vague "tool" / "gem" / "prop"). */
public final class ItemTemplateCatalog {

    public record Entry(
            String id,
            String label,
            String group,
            String defaultAbility,
            boolean furniture) {
    }

    private static final Map<String, Entry> BY_ID = new LinkedHashMap<>();

    static {
        // Weapons
        add("sword", "Sword", "weapon", "lightning_dash", false);
        add("axe", "Axe", "weapon", "ground_slam", false);
        add("spear", "Spear", "weapon", "smite_target", false);
        add("mace", "Mace", "weapon", "ground_slam", false);
        add("trident", "Trident", "weapon", "smite_target", false);
        add("bow", "Bow", "weapon", "projectile", false);
        add("crossbow", "Crossbow", "weapon", "projectile", false);
        add("shield", "Shield", "weapon", "absorb", false);
        add("wand", "Wand (blaze rod)", "weapon", "effect", false);
        // Tools
        add("pickaxe", "Pickaxe", "tool", "break_block", false);
        add("shovel", "Shovel", "tool", "break_block", false);
        add("hoe", "Hoe", "tool", "break_block", false);
        add("shears", "Shears", "tool", "break_block", false);
        add("fishing_rod", "Fishing rod", "tool", "pull", false);
        // Gems / charms
        add("amethyst", "Amethyst shard", "gem", "heal", false);
        add("emerald", "Emerald", "gem", "heal", false);
        add("diamond", "Diamond", "gem", "effect", false);
        add("nether_star", "Nether star", "gem", "absorb", false);
        add("echo_shard", "Echo shard", "gem", "blink", false);
        add("totem", "Totem", "gem", "absorb", false);
        add("golden_apple", "Golden apple", "gem", "heal", false);
        add("heart_of_the_sea", "Heart of the sea", "gem", "effect", false);
        add("prismarine", "Prismarine shard", "gem", "effect", false);
        // Props (placeable furniture — pick the look)
        add("prop_paper", "Prop: blank paper", "prop", "none", true);
        add("prop_oak", "Prop: oak planks", "prop", "none", true);
        add("prop_stone", "Prop: stone", "prop", "none", true);
        add("prop_chest", "Prop: chest", "prop", "none", true);
        add("prop_ender_chest", "Prop: ender chest", "prop", "none", true);
        add("prop_lantern", "Prop: lantern", "prop", "none", true);
        add("prop_soul_lantern", "Prop: soul lantern", "prop", "none", true);
        add("prop_beacon", "Prop: beacon", "prop", "none", true);
        add("prop_pot", "Prop: flower pot", "prop", "none", true);
        add("prop_bell", "Prop: bell", "prop", "none", true);
        add("prop_armor_stand", "Prop: armor stand", "prop", "none", true);
        // Other
        add("key", "Key", "other", "message", false);
    }

    private ItemTemplateCatalog() {
    }

    private static void add(String id, String label, String group, String ability, boolean furniture) {
        BY_ID.put(id, new Entry(id, label, group, ability, furniture));
    }

    public static Entry get(String id) {
        if (id == null) {
            return null;
        }
        return BY_ID.get(id.toLowerCase(Locale.ROOT));
    }

    public static String label(String id) {
        Entry e = get(id);
        return e == null ? id : e.label();
    }

    public static Iterable<Entry> all() {
        return BY_ID.values();
    }

    public static List<Entry> byGroup(String group) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : BY_ID.values()) {
            if (e.group().equals(group)) {
                out.add(e);
            }
        }
        return out;
    }

    public static String[] groupOrder() {
        return new String[] {"weapon", "tool", "gem", "prop", "other"};
    }

    public static String groupTitle(String group) {
        return switch (group) {
            case "weapon" -> "Weapons";
            case "tool" -> "Tools";
            case "gem" -> "Gems / charms";
            case "prop" -> "Props (placeable look)";
            case "other" -> "Other";
            default -> group;
        };
    }
}
