package com.yapcore.skills.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SkillsMenuHolder implements InventoryHolder {

    private final UUID viewer;
    private Inventory inventory;

    public SkillsMenuHolder(UUID viewer) {
        this.viewer = viewer;
    }

    public UUID viewer() {
        return viewer;
    }

    public void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private static void applyCmd(ItemMeta meta, int cmd) {
        if (meta == null || cmd <= 0) {
            return;
        }
        try {
            meta.setCustomModelData(cmd);
        } catch (Throwable ignored) {
        }
    }

    public static ItemStack filler() {
        ItemStack stack = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        stack.editMeta(meta -> meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false)));
        return stack;
    }

    public static ItemStack overallLevelIcon(
            int overallLevel,
            int totalLevel,
            double totalXp,
            double xpIntoLevel,
            double xpToNext,
            int maxLevel) {
        ItemStack stack = new ItemStack(Material.NETHER_STAR);
        stack.editMeta(meta -> {
            meta.displayName(Component.text("Overall Level")
                    .color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Level " + overallLevel + " / " + maxLevel)
                    .color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Total skill levels: " + totalLevel)
                    .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(String.format("Overall XP: %.0f", totalXp))
                    .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            if (overallLevel < maxLevel) {
                lore.add(Component.text(String.format("Progress: %.0f / %.0f", xpIntoLevel, xpToNext))
                        .color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("Max overall level")
                        .color(NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.text("Gains XP from every skill action")
                    .color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        });
        return stack;
    }

    public static ItemStack combatLevelIcon(int combatLevel, int maxLevel) {
        ItemStack stack = new ItemStack(Material.CLAY_BALL);
        stack.editMeta(meta -> {
            applyCmd(meta, 79000);
            meta.displayName(Component.text("Combat Level")
                    .color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Level " + combatLevel)
                    .color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Avg of Attack, Strength,")
                    .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Defence & Hitpoints (max " + maxLevel + ")")
                    .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        });
        return stack;
    }

    public static ItemStack skillIcon(
            Material icon,
            int iconCmd,
            String name,
            int level,
            double xp,
            double xpIntoLevel,
            double xpToNext,
            int maxLevel) {
        ItemStack stack = new ItemStack(icon == null ? Material.CLAY_BALL : icon);
        stack.editMeta(meta -> {
            applyCmd(meta, iconCmd);
            meta.displayName(Component.text(name).color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Level " + level + " / " + maxLevel)
                    .color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(String.format("Total XP: %.0f", xp))
                    .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            if (level < maxLevel) {
                lore.add(Component.text(String.format("Progress: %.0f / %.0f", xpIntoLevel, xpToNext))
                        .color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("Max level reached")
                        .color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
        });
        return stack;
    }
}
