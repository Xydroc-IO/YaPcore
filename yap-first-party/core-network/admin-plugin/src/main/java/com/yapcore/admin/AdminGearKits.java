package com.yapcore.admin;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Armor / weapon give bundles for YaPAdmin Give hub. */
public final class AdminGearKits {

    public record GearItem(Material material, int amount) {
    }

    public record GearKit(String id, String displayName, Material icon, List<GearItem> items) {
    }

    private AdminGearKits() {
    }

    public static List<GearKit> load(FileConfiguration c) {
        List<GearKit> loaded = new ArrayList<>();
        List<?> raw = c.getList("gear-kits");
        if (raw != null) {
            for (Object entry : raw) {
                GearKit kit = parseEntry(entry);
                if (kit != null) {
                    loaded.add(kit);
                }
            }
        }
        return loaded.isEmpty() ? builtins() : List.copyOf(loaded);
    }

    public static List<GearKit> builtins() {
        return List.of(
                armor("leather_armor", "Leather Armor", Material.LEATHER_CHESTPLATE,
                        Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE,
                        Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS),
                armor("chainmail_armor", "Chainmail Armor", Material.CHAINMAIL_CHESTPLATE,
                        Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE,
                        Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS),
                armor("iron_armor", "Iron Armor", Material.IRON_CHESTPLATE,
                        Material.IRON_HELMET, Material.IRON_CHESTPLATE,
                        Material.IRON_LEGGINGS, Material.IRON_BOOTS),
                armor("gold_armor", "Gold Armor", Material.GOLDEN_CHESTPLATE,
                        Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE,
                        Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS),
                armor("diamond_armor", "Diamond Armor", Material.DIAMOND_CHESTPLATE,
                        Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE,
                        Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS),
                armor("netherite_armor", "Netherite Armor", Material.NETHERITE_CHESTPLATE,
                        Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE,
                        Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS),
                weapons("iron_weapons", "Iron Weapons", Material.IRON_SWORD,
                        Material.IRON_SWORD, Material.IRON_AXE, Material.BOW, Material.SHIELD),
                weapons("diamond_weapons", "Diamond Weapons", Material.DIAMOND_SWORD,
                        Material.DIAMOND_SWORD, Material.DIAMOND_AXE, Material.BOW, Material.SHIELD),
                weapons("netherite_weapons", "Netherite Weapons", Material.NETHERITE_SWORD,
                        Material.NETHERITE_SWORD, Material.NETHERITE_AXE, Material.BOW,
                        Material.CROSSBOW, Material.SHIELD),
                new GearKit("archery", "Archery Kit", Material.BOW, List.of(
                        new GearItem(Material.BOW, 1),
                        new GearItem(Material.CROSSBOW, 1),
                        new GearItem(Material.ARROW, 64),
                        new GearItem(Material.SPECTRAL_ARROW, 16),
                        new GearItem(Material.SHIELD, 1))),
                new GearKit("iron_tools", "Iron Tools", Material.IRON_PICKAXE, List.of(
                        new GearItem(Material.IRON_PICKAXE, 1),
                        new GearItem(Material.IRON_AXE, 1),
                        new GearItem(Material.IRON_SHOVEL, 1),
                        new GearItem(Material.IRON_HOE, 1))),
                new GearKit("diamond_tools", "Diamond Tools", Material.DIAMOND_PICKAXE, List.of(
                        new GearItem(Material.DIAMOND_PICKAXE, 1),
                        new GearItem(Material.DIAMOND_AXE, 1),
                        new GearItem(Material.DIAMOND_SHOVEL, 1),
                        new GearItem(Material.DIAMOND_HOE, 1))),
                new GearKit("netherite_tools", "Netherite Tools", Material.NETHERITE_PICKAXE, List.of(
                        new GearItem(Material.NETHERITE_PICKAXE, 1),
                        new GearItem(Material.NETHERITE_AXE, 1),
                        new GearItem(Material.NETHERITE_SHOVEL, 1),
                        new GearItem(Material.NETHERITE_HOE, 1)))
        );
    }

    private static GearKit armor(String id, String name, Material icon, Material... pieces) {
        List<GearItem> items = new ArrayList<>(pieces.length);
        for (Material piece : pieces) {
            items.add(new GearItem(piece, 1));
        }
        return new GearKit(id, name, icon, List.copyOf(items));
    }

    private static GearKit weapons(String id, String name, Material icon, Material... pieces) {
        List<GearItem> items = new ArrayList<>();
        for (Material piece : pieces) {
            items.add(new GearItem(piece, 1));
        }
        items.add(new GearItem(Material.ARROW, 64));
        return new GearKit(id, name, icon, List.copyOf(items));
    }

    private static GearKit parseEntry(Object entry) {
        ConfigurationSection sec = asSection(entry);
        if (sec == null) {
            return null;
        }
        String id = sec.getString("id", "");
        if (id.isBlank()) {
            return null;
        }
        String name = sec.getString("name", id);
        Material icon = Material.matchMaterial(sec.getString("icon", "CHEST"));
        if (icon == null || !icon.isItem()) {
            icon = Material.CHEST;
        }
        List<GearItem> items = new ArrayList<>();
        List<?> rawItems = sec.getList("items");
        if (rawItems != null) {
            for (Object rawItem : rawItems) {
                GearItem item = parseItem(rawItem);
                if (item != null) {
                    items.add(item);
                }
            }
        }
        if (items.isEmpty()) {
            return null;
        }
        return new GearKit(id.trim().toLowerCase(), name, icon, List.copyOf(items));
    }

    private static GearItem parseItem(Object raw) {
        if (raw instanceof String s) {
            String[] parts = s.split(":");
            Material mat = Material.matchMaterial(parts[0].trim());
            if (mat == null || !mat.isItem()) {
                return null;
            }
            int amount = 1;
            if (parts.length > 1) {
                try {
                    amount = Math.max(1, Integer.parseInt(parts[1].trim()));
                } catch (NumberFormatException ignored) {
                    amount = 1;
                }
            }
            return new GearItem(mat, Math.min(amount, mat.getMaxStackSize()));
        }
        ConfigurationSection sec = asSection(raw);
        if (sec == null) {
            return null;
        }
        Material mat = Material.matchMaterial(sec.getString("material", ""));
        if (mat == null || !mat.isItem()) {
            return null;
        }
        int amount = Math.max(1, sec.getInt("amount", 1));
        return new GearItem(mat, Math.min(amount, mat.getMaxStackSize()));
    }

    @SuppressWarnings("unchecked")
    private static ConfigurationSection asSection(Object entry) {
        if (entry instanceof ConfigurationSection cs) {
            return cs;
        }
        if (entry instanceof Map<?, ?> map) {
            MemoryConfiguration sec = new MemoryConfiguration();
            for (var e : ((Map<String, Object>) map).entrySet()) {
                sec.set(e.getKey(), e.getValue());
            }
            return sec;
        }
        return null;
    }
}
