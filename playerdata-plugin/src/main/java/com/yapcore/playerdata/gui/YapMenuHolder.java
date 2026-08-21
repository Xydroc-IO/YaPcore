package com.yapcore.playerdata.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Typed inventory holder so click handlers never rely on title strings alone. */
public final class YapMenuHolder implements InventoryHolder {

    public enum Kind {
        HUB, HOMES, WARPS, KITS, JOBS, AUCTIONS, CLAIMS, MAIL, CLAIM_DETAIL, NPC_TRADER
    }

    private final Kind kind;
    private final Object context;
    private Inventory inventory;

    public YapMenuHolder(Kind kind) {
        this(kind, null);
    }

    public YapMenuHolder(Kind kind, Object context) {
        this.kind = kind;
        this.context = context;
    }

    public Kind kind() {
        return kind;
    }

    @SuppressWarnings("unchecked")
    public <T> T context() {
        return (T) context;
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
            List<Component> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(Component.text(line).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lines);
        });
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
