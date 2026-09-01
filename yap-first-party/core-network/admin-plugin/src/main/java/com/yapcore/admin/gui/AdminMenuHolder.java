package com.yapcore.admin.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Typed holder for YaPAdmin menus. */
public final class AdminMenuHolder implements InventoryHolder {

    public enum Kind {
        HUB,
        PLAYERS,
        PLAYER_ACTIONS,
        SELF_TOOLS,
        GIVE_HUB,
        GIVE_PRESETS,
        GIVE_KITS,
        GIVE_MATERIALS,
        SERVER_OPS,
        ECONOMY,
        DEEP_LINKS,
        COMBAT_SKILLS
    }

    private final Kind kind;
    private final UUID targetUuid;
    private final String targetName;
    private Inventory inventory;

    public AdminMenuHolder(Kind kind) {
        this(kind, null, null);
    }

    public AdminMenuHolder(Kind kind, UUID targetUuid, String targetName) {
        this.kind = kind;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
    }

    public Kind kind() {
        return kind;
    }

    public UUID targetUuid() {
        return targetUuid;
    }

    public String targetName() {
        return targetName;
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

    public static void fillAll(Inventory inv) {
        ItemStack filler = filler();
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }
    }
}
