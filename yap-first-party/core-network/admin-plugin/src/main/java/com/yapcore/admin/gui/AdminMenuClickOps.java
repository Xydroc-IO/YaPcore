package com.yapcore.admin.gui;

import com.yapcore.admin.AdminPlugin;
import com.yapcore.admin.action.AdminActions;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Click handlers for server ops, economy, deep links, and combat skills. */
final class AdminMenuClickOps {

    private final AdminPlugin plugin;

    AdminMenuClickOps(AdminPlugin plugin) {
        this.plugin = plugin;
    }

    void handleServerOps(Player player, int slot, ItemStack clicked) {
        if (slot == AdminMenus.SLOT_BACK) {
            plugin.menus().openHub(player);
            return;
        }
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == 28) {
            plugin.actions().closeAndRun(player, "yapperm gui");
            return;
        }
        if (slot == 30) {
            if (plugin.actions().pluginEnabled("YaPDisasters")) {
                plugin.actions().closeAndRun(player, "yapdisaster");
            } else {
                // Essentials /weather with no args prints clear|rain|… help (or disasters install tip).
                plugin.actions().closeAndRun(player, "weather clear");
            }
            return;
        }
        if (clicked == null || clicked.getType() != Material.NOTE_BLOCK) {
            return;
        }
        String title = AdminMenuItemSupport.plainName(clicked);
        if (!title.startsWith("Broadcast #")) {
            return;
        }
        try {
            int idx = Integer.parseInt(title.replace("Broadcast #", "").trim()) - 1;
            var presets = plugin.adminConfig().broadcastPresets();
            if (idx >= 0 && idx < presets.size()) {
                plugin.actions().broadcast(player, presets.get(idx));
            }
        } catch (NumberFormatException ignored) {
        }
    }

    void handleEconomy(Player player, int slot, ItemStack clicked) {
        if (slot == AdminMenus.SLOT_BACK) {
            plugin.menus().openHub(player);
            return;
        }
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (clicked == null || clicked.getType() != Material.EMERALD) {
            return;
        }
        String name = AdminMenuItemSupport.plainName(clicked);
        if (!name.startsWith("+")) {
            return;
        }
        try {
            int amount = Integer.parseInt(name.substring(1).replace(",", "").trim());
            Player target = plugin.actions().resolveGiveTarget(player);
            plugin.actions().giveMoney(player, target, amount);
        } catch (NumberFormatException ignored) {
        }
    }

    void handleDeepLinks(Player player, int slot) {
        if (slot == AdminMenus.SLOT_BACK) {
            plugin.menus().openHub(player);
            return;
        }
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        AdminActions actions = plugin.actions();
        switch (slot) {
            case 19 -> actions.closeAndRun(player, "yapperm gui");
            case 20 -> actions.closeAndRun(player, "yapworld gui");
            case 21 -> actions.closeAndRun(player, "yapstacker gui");
            case 22 -> actions.closeAndRun(player, "menu");
            case 23 -> actions.closeAndRun(player, "yapworld schem browse");
            case 24 -> actions.closeAndRun(player, "yappregen status");
            default -> {
            }
        }
    }

    void handleCombatSkills(Player player, int slot) {
        if (slot == AdminMenus.SLOT_BACK) {
            plugin.menus().openHub(player);
            return;
        }
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        AdminActions actions = plugin.actions();
        switch (slot) {
            case 20 -> actions.closeAndRun(player, "skills");
            case 22 -> actions.heal(player, player);
            case 24 -> actions.closeAndRun(player, "prayer list");
            default -> {
            }
        }
    }
}
