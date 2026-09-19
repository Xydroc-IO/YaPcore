package com.yapcore.skills.power;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Locale;
import java.util.Map;

/** Fully-grown crop checks and Green Terra replant seeds. */
public final class SkillCrops {

    private static final Map<Material, Material> SEEDS = Map.ofEntries(
            Map.entry(Material.WHEAT, Material.WHEAT_SEEDS),
            Map.entry(Material.CARROTS, Material.CARROT),
            Map.entry(Material.POTATOES, Material.POTATO),
            Map.entry(Material.BEETROOTS, Material.BEETROOT_SEEDS),
            Map.entry(Material.NETHER_WART, Material.NETHER_WART),
            Map.entry(Material.COCOA, Material.COCOA_BEANS),
            Map.entry(Material.TORCHFLOWER_CROP, Material.TORCHFLOWER_SEEDS),
            Map.entry(Material.PITCHER_CROP, Material.PITCHER_POD),
            Map.entry(Material.SWEET_BERRY_BUSH, Material.SWEET_BERRIES)
    );

    private SkillCrops() {
    }

    public static boolean isCrop(Material type) {
        if (type == null) {
            return false;
        }
        if (SEEDS.containsKey(type)) {
            return true;
        }
        String name = type.name();
        return name.endsWith("_CROP") || "WHEAT".equals(name) || "CARROTS".equals(name)
                || "POTATOES".equals(name) || "BEETROOTS".equals(name)
                || "COCOA".equals(name) || "NETHER_WART".equals(name)
                || "SWEET_BERRY_BUSH".equals(name);
    }

    public static boolean isMature(Block block) {
        if (block == null) {
            return false;
        }
        BlockData data = block.getBlockData();
        if (!(data instanceof Ageable ageable)) {
            return true;
        }
        return ageable.getAge() >= ageable.getMaximumAge();
    }

    public static boolean canReplant(Material type) {
        return type != null && SEEDS.containsKey(type);
    }

    public static BlockData replantData(BlockData before) {
        if (!(before instanceof Ageable ageable)) {
            return before;
        }
        int nextAge = before.getMaterial() == Material.SWEET_BERRY_BUSH ? 1 : 0;
        ageable.setAge(Math.min(nextAge, ageable.getMaximumAge()));
        return ageable;
    }

    public static boolean consumeSeed(PlayerInventory inv, Material crop) {
        Material seed = SEEDS.get(crop);
        if (inv == null || seed == null) {
            return false;
        }
        if (!inv.contains(seed)) {
            return false;
        }
        inv.removeItem(new ItemStack(seed, 1));
        return true;
    }

    public static boolean isPickaxe(Material type) {
        return type != null && type.name().toUpperCase(Locale.ROOT).endsWith("_PICKAXE");
    }

    public static boolean isAxe(Material type) {
        return type != null && type.name().toUpperCase(Locale.ROOT).endsWith("_AXE")
                && !type.name().toUpperCase(Locale.ROOT).contains("PICK");
    }
}
