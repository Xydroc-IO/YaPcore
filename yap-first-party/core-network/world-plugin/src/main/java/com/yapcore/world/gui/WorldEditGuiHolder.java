package com.yapcore.world.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/** Typed inventory holder for YaPWorld editor GUIs. */
public final class WorldEditGuiHolder implements InventoryHolder {

    public enum Kind {
        MAIN, SCHEMATICS
    }

    private final Kind kind;
    private Inventory inventory;

    public WorldEditGuiHolder(Kind kind) {
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    public static ItemStack icon(Material mat, String name, String... lore) {
        return icon(mat, NamedTextColor.YELLOW, name, lore);
    }

    public static ItemStack icon(Material mat, NamedTextColor color, String name, String... lore) {
        ItemStack stack = new ItemStack(mat);
        stack.editMeta(meta -> {
            meta.displayName(Component.text(name).color(color).decoration(TextDecoration.ITALIC, false));
            java.util.List<Component> lines = new java.util.ArrayList<>();
            for (String line : lore) {
                lines.add(Component.text(line).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lines);
        });
        return stack;
    }

    public static ItemStack selected(Material mat, String name) {
        ItemStack stack = icon(mat, NamedTextColor.GREEN, name, "Selected material");
        stack.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        return stack;
    }

    public static ItemStack filler() {
        ItemStack stack = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        stack.editMeta(meta -> meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false)));
        return stack;
    }

    public static void fillBorder(Inventory inv) {
        ItemStack pane = filler();
        int size = inv.getSize();
        for (int i = 0; i < size; i++) {
            int row = i / 9;
            int col = i % 9;
            if (row == 0 || row == (size / 9) - 1 || col == 0 || col == 8) {
                if (inv.getItem(i) == null) {
                    inv.setItem(i, pane);
                }
            }
        }
    }
}
