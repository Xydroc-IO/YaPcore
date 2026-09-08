package com.yapcore.admin.gui;

import org.bukkit.Material;

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
            Material icon,
            String defaultAbility,
            boolean furniture) {
    }

    private static final Map<String, Entry> BY_ID = new LinkedHashMap<>();

    static {
        add("sword", "Sword", "weapon", Material.NETHERITE_SWORD, "lightning_dash", false);
        add("axe", "Axe", "weapon", Material.NETHERITE_AXE, "ground_slam", false);
        add("spear", "Spear", "weapon", Material.NETHERITE_SPEAR, "smite_target", false);
        add("mace", "Mace", "weapon", Material.MACE, "ground_slam", false);
        add("trident", "Trident", "weapon", Material.TRIDENT, "smite_target", false);
        add("bow", "Bow", "weapon", Material.BOW, "projectile", false);
        add("crossbow", "Crossbow", "weapon", Material.CROSSBOW, "projectile", false);
        add("shield", "Shield", "weapon", Material.SHIELD, "absorb", false);
        add("wand", "Wand (blaze rod)", "weapon", Material.BLAZE_ROD, "effect", false);

        add("pickaxe", "Pickaxe", "tool", Material.NETHERITE_PICKAXE, "break_block", false);
        add("shovel", "Shovel", "tool", Material.NETHERITE_SHOVEL, "break_block", false);
        add("hoe", "Hoe", "tool", Material.NETHERITE_HOE, "break_block", false);
        add("shears", "Shears", "tool", Material.SHEARS, "break_block", false);
        add("fishing_rod", "Fishing rod", "tool", Material.FISHING_ROD, "pull", false);

        add("amethyst", "Amethyst shard", "gem", Material.AMETHYST_SHARD, "heal", false);
        add("emerald", "Emerald", "gem", Material.EMERALD, "heal", false);
        add("diamond", "Diamond", "gem", Material.DIAMOND, "effect", false);
        add("nether_star", "Nether star", "gem", Material.NETHER_STAR, "absorb", false);
        add("echo_shard", "Echo shard", "gem", Material.ECHO_SHARD, "blink", false);
        add("totem", "Totem", "gem", Material.TOTEM_OF_UNDYING, "absorb", false);
        add("golden_apple", "Golden apple", "gem", Material.GOLDEN_APPLE, "heal", false);
        add("heart_of_the_sea", "Heart of the sea", "gem", Material.HEART_OF_THE_SEA, "effect", false);
        add("prismarine", "Prismarine shard", "gem", Material.PRISMARINE_SHARD, "effect", false);

        add("prop_paper", "Prop: blank paper", "prop", Material.PAPER, "", true);
        add("prop_oak", "Prop: oak planks", "prop", Material.OAK_PLANKS, "", true);
        add("prop_stone", "Prop: stone", "prop", Material.STONE, "", true);
        add("prop_chest", "Prop: chest", "prop", Material.CHEST, "", true);
        add("prop_ender_chest", "Prop: ender chest", "prop", Material.ENDER_CHEST, "", true);
        add("prop_lantern", "Prop: lantern", "prop", Material.LANTERN, "", true);
        add("prop_soul_lantern", "Prop: soul lantern", "prop", Material.SOUL_LANTERN, "", true);
        add("prop_beacon", "Prop: beacon", "prop", Material.BEACON, "", true);
        add("prop_pot", "Prop: flower pot", "prop", Material.FLOWER_POT, "", true);
        add("prop_bell", "Prop: bell", "prop", Material.BELL, "", true);
        add("prop_armor_stand", "Prop: armor stand", "prop", Material.ARMOR_STAND, "", true);

        add("key", "Key", "other", Material.TRIPWIRE_HOOK, "message", false);
    }

    private ItemTemplateCatalog() {
    }

    private static void add(String id, String label, String group, Material icon, String ability, boolean furniture) {
        BY_ID.put(id, new Entry(id, label, group, icon, ability, furniture));
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
