package com.yapcore.admin.gui;

import com.yapcore.admin.AdminPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * Single World tools panel: world edit + schematics browse + paste-preview controls.
 * Replaces the old split “Schematics hub tile” vs “More… → World edit / Schematics”.
 */
final class AdminMenusWorldTools {

    private final AdminPlugin plugin;

    AdminMenusWorldTools(AdminPlugin plugin) {
        this.plugin = plugin;
    }

    void openSchematics(Player player) {
        if (!plugin.actions().pluginEnabled("YaPWorld")) {
            player.sendMessage("§cYaPWorld is not loaded.");
            return;
        }
        String previewLabel = worldToolsPreviewLabel(player);
        Integer previewYaw = worldToolsPreviewYaw(player);
        boolean preview = previewLabel != null;

        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.SCHEMATICS);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("World tools", NamedTextColor.AQUA));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);

        if (preview) {
            inv.setItem(AdminMenuSlots.SLOT_INFO, AdminMenuHolder.icon(Material.LIME_DYE, NamedTextColor.GREEN,
                    "Preview ready",
                    "Pending: " + previewLabel + " · " + previewYaw + "°",
                    "Move · Rotate · Confirm — outline updates live",
                    "Shift-click Rotate = counter-clockwise"));
        } else {
            inv.setItem(AdminMenuSlots.SLOT_INFO, AdminMenuHolder.icon(Material.WOODEN_AXE, NamedTextColor.AQUA,
                    "World tools",
                    "World edit — selection, fill, clipboard",
                    "Schematics — browse → preview → rotate → confirm",
                    "Paste controls light up when a preview is active"));
        }

        inv.setItem(19, AdminMenuHolder.icon(Material.WOODEN_AXE, "World edit",
                "Selection · fill · clipboard · shapes"));
        inv.setItem(20, AdminMenuHolder.icon(Material.CHEST, "Schematics",
                "Browse files — click one for a paste outline"));
        inv.setItem(21, AdminMenuHolder.icon(Material.SPYGLASS, "Browser studio",
                "Open the web WorldEdit studio",
                "/yapworld editor"));
        if (plugin.actions().pluginEnabled("YaPPregen")) {
            inv.setItem(22, AdminMenuHolder.icon(Material.RECOVERY_COMPASS, "Pregen",
                    "Chunk pre-generator status"));
        }

        if (preview) {
            inv.setItem(28, AdminMenuHolder.icon(Material.LIME_CONCRETE, NamedTextColor.GREEN, "Confirm paste",
                    "Place “" + previewLabel + "” at " + previewYaw + "°"));
            inv.setItem(29, AdminMenuHolder.icon(Material.RED_CONCRETE, NamedTextColor.RED, "Cancel preview",
                    "Abort pending paste outline"));
            inv.setItem(30, AdminMenuHolder.icon(Material.COMPASS, NamedTextColor.YELLOW, "Move here",
                    "Shift paste box to your feet",
                    "Walk first, then click"));
            inv.setItem(32, AdminMenuHolder.icon(Material.REPEATER, NamedTextColor.YELLOW, "Rotate 90°",
                    "Now at " + previewYaw + "° · click = CW",
                    "Shift-click = CCW · //schem rotate"));
            inv.setItem(33, AdminMenuHolder.icon(Material.PISTON, "Flip X",
                    "Mirror east↔west",
                    "Shift-click = Flip Z"));
        } else {
            inv.setItem(28, AdminMenuHolder.icon(Material.GRAY_CONCRETE, "Confirm paste",
                    "No preview yet — browse a schematic first"));
            inv.setItem(29, AdminMenuHolder.icon(Material.GRAY_CONCRETE, "Cancel preview",
                    "No preview active"));
            inv.setItem(30, AdminMenuHolder.icon(Material.GRAY_CONCRETE, "Move here",
                    "No preview active"));
            inv.setItem(32, AdminMenuHolder.icon(Material.GRAY_CONCRETE, "Rotate 90°",
                    "No preview active"));
            inv.setItem(33, AdminMenuHolder.icon(Material.GRAY_CONCRETE, "Flip X",
                    "No preview active"));
        }
        inv.setItem(31, AdminMenuHolder.icon(Material.ORANGE_CONCRETE, NamedTextColor.GOLD, "Undo paste",
                "Undo last paste / fill / set",
                "Also: //undo · //schem undo"));

        inv.setItem(AdminMenuSlots.SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(AdminMenuSlots.SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    /** Soft-dep read of YaPWorld {@code pastePreview().get(uuid).label()} — null when none. */
    private static String worldToolsPreviewLabel(Player player) {
        Object pending = worldToolsPending(player);
        if (pending == null) {
            return null;
        }
        try {
            Object label = pending.getClass().getMethod("label").invoke(pending);
            return label == null ? "schematic" : label.toString();
        } catch (ReflectiveOperationException e) {
            return "schematic";
        }
    }

    private static Integer worldToolsPreviewYaw(Player player) {
        Object pending = worldToolsPending(player);
        if (pending == null) {
            return 0;
        }
        try {
            Object yaw = pending.getClass().getMethod("yawDegrees").invoke(pending);
            return yaw instanceof Integer i ? i : 0;
        } catch (ReflectiveOperationException e) {
            return 0;
        }
    }

    private static Object worldToolsPending(Player player) {
        var pl = Bukkit.getPluginManager().getPlugin("YaPWorld");
        if (pl == null || !pl.isEnabled()) {
            return null;
        }
        try {
            Object preview = pl.getClass().getMethod("pastePreview").invoke(pl);
            if (preview == null) {
                return null;
            }
            Object opt = preview.getClass().getMethod("get", java.util.UUID.class)
                    .invoke(preview, player.getUniqueId());
            if (!(opt instanceof java.util.Optional<?> o) || o.isEmpty()) {
                return null;
            }
            return o.get();
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
