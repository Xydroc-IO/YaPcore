package com.yapcore.qol;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

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
        ItemStack stack = labeled(
                config.excavatorMaterial(),
                EXCAVATOR,
                config.excavatorName(),
                withSizeLore(config.excavatorLore(), resolved),
                config.excavatorGlow(),
                config.excavatorUnbreakable(),
                resolved);
        return stack;
    }

    public ItemStack create(String type) {
        String key = type.toLowerCase().trim();
        if (key.startsWith("excavator:") || key.startsWith("excavator_")) {
            String num = key.substring("excavator".length() + 1);
            try {
                return createExcavator(Integer.parseInt(num));
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
        ItemStack item = new ItemStack(mat);
        item.editMeta(meta -> {
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
            if (glow) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        });
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

    public void give(Player player, String type) {
        player.getInventory().addItem(create(type));
    }

    public void giveExcavator(Player player, int size) {
        player.getInventory().addItem(createExcavator(size));
    }
}
