package com.yapcore.qol;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/** Factory for timber axe and area excavator items. */
public final class QolItems {

    public static final String TIMBER_AXE = "timber_axe";
    public static final String EXCAVATOR = "excavator";

    private final QolConfig config;
    private final QolKeys keys;

    public QolItems(QolConfig config, QolKeys keys) {
        this.config = config;
        this.keys = keys;
    }

    public ItemStack createTimberAxe() {
        return labeled(
                config.timberMaterial(),
                TIMBER_AXE,
                config.timberName(),
                config.timberLore(),
                config.timberGlow(),
                config.timberUnbreakable(),
                0);
    }

    public ItemStack createExcavator() {
        return createExcavator(config.defaultSize());
    }

    public ItemStack createExcavator(int size) {
        int resolved = config.nearestSize(size);
        return labeled(
                config.excavatorMaterial(),
                EXCAVATOR,
                config.excavatorName(),
                withSizeLore(config.excavatorLore(), resolved),
                config.excavatorGlow(),
                config.excavatorUnbreakable(),
                resolved);
    }

    public ItemStack create(String type) {
        String key = type.toLowerCase(Locale.ROOT).trim().replace('-', '_');
        if (key.startsWith("excavator:")) {
            try {
                return createExcavator(Integer.parseInt(key.substring("excavator:".length())));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Unknown tool: " + type);
            }
        }
        // excavator_3 / excavator_6 (not bare "excavator")
        if (key.startsWith("excavator_") && key.length() > "excavator_".length()) {
            try {
                return createExcavator(Integer.parseInt(key.substring("excavator_".length())));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Unknown tool: " + type);
            }
        }
        return switch (key) {
            case TIMBER_AXE, "timber", "axe" -> createTimberAxe();
            case EXCAVATOR, "pick", "pickaxe", "hammer" -> createExcavator();
            default -> throw new IllegalArgumentException("Unknown tool: " + type);
        };
    }

    private ItemStack labeled(
            Material mat,
            String type,
            String name,
            List<String> loreLines,
            boolean glow,
            boolean unbreakable,
            int mineSize) {
        if (mat == null || mat.isAir() || !mat.isItem()) {
            throw new IllegalArgumentException("Invalid QoL material for " + type + ": " + mat);
        }
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("No ItemMeta for material " + mat + " (tool=" + type + ")");
        }
        meta.displayName(QolConfig.legacy().deserialize(name)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(QolConfig.legacy().deserialize(line)
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(keys.toolType, PersistentDataType.STRING, type);
        if (EXCAVATOR.equals(type)) {
            meta.getPersistentDataContainer().set(keys.mineSize, PersistentDataType.INTEGER, mineSize);
        }
        meta.setUnbreakable(unbreakable);
        // 26.2: prefer glint override over a fake Unbreaking enchant (meta/handle sync quirks).
        if (glow) {
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
        }
        if (!item.setItemMeta(meta)) {
            throw new IllegalStateException("Failed to apply ItemMeta for " + type + " (" + mat + ")");
        }
        if (glow) {
            item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, Boolean.TRUE);
        } else {
            item.unsetData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE);
        }
        // Refuse to hand out an untagged stack (would look like a normal tool).
        if (toolType(item) == null) {
            throw new IllegalStateException("QoL PDC missing after build for " + type);
        }
        return item;
    }

    private static List<String> withSizeLore(List<String> base, int size) {
        List<String> out = new ArrayList<>(base);
        out.add("&7Mine size: &f" + size + "×" + size);
        return out;
    }

    public String toolType(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer()
                .get(keys.toolType, PersistentDataType.STRING);
    }

    public boolean isTimberAxe(ItemStack stack) {
        return TIMBER_AXE.equals(toolType(stack));
    }

    public boolean isExcavator(ItemStack stack) {
        return EXCAVATOR.equals(toolType(stack));
    }

    public int mineSize(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return config.defaultSize();
        }
        Integer stored = stack.getItemMeta().getPersistentDataContainer()
                .get(keys.mineSize, PersistentDataType.INTEGER);
        if (stored == null) {
            return config.defaultSize();
        }
        return config.nearestSize(stored);
    }

    public void setMineSize(ItemStack stack, int size) {
        if (stack == null || !isExcavator(stack)) {
            return;
        }
        int resolved = config.nearestSize(size);
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(keys.mineSize, PersistentDataType.INTEGER, resolved);
        List<Component> lore = new ArrayList<>();
        for (String line : withSizeLore(config.excavatorLore(), resolved)) {
            lore.add(QolConfig.legacy().deserialize(line)
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
    }

    /**
     * Gives the tool. Overflow drops at the player's feet (never silent-fail like bare {@code addItem}).
     *
     * @return {@code true} if the inventory took the whole stack; {@code false} if anything was dropped
     */
    public boolean give(Player player, String type) {
        return giveStack(player, create(type));
    }

    public boolean giveExcavator(Player player, int size) {
        return giveStack(player, createExcavator(size));
    }

    private static boolean giveStack(Player player, ItemStack stack) {
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        if (leftover.isEmpty()) {
            return true;
        }
        for (ItemStack extra : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
        return false;
    }
}
