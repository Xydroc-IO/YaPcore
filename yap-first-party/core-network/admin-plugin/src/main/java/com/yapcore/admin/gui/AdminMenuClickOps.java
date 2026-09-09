package com.yapcore.admin.gui;

import com.yapcore.admin.AdminPlugin;
import com.yapcore.admin.action.AdminActions;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
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
        if (slot == 32) {
            plugin.actions().closeAndRun(player, "yapess keepinventory toggle");
            return;
        }
        if (slot == 34) {
            plugin.menus().openLeveledMobs(player);
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
            case 23 -> plugin.menus().openSchematics(player);
            case 24 -> actions.closeAndRun(player, "yappregen status");
            case 25 -> plugin.menus().openLeveledMobs(player);
            default -> {
            }
        }
    }

    void handleSchematics(Player player, int slot) {
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
            case 20 -> actions.closeAndRun(player, "yapworld schem browse");
            case 22 -> actions.closeAndRun(player, "yapworld gui");
            case 28 -> {
                // Place then leave menu so the world is visible.
                actions.closeAndRun(player, "yapworld schem confirm");
            }
            case 29 -> {
                player.closeInventory();
                YapSched.entityLater(plugin, player, () -> {
                    Bukkit.dispatchCommand(player, "yapworld schem cancel");
                    plugin.menus().openSchematics(player);
                }, 1L);
            }
            case 30 -> {
                // Close so feet position is intentional, move, reopen for Confirm.
                player.closeInventory();
                YapSched.entityLater(plugin, player, () -> {
                    Bukkit.dispatchCommand(player, "yapworld schem here");
                    YapSched.entityLater(plugin, player, () -> plugin.menus().openSchematics(player), 2L);
                }, 1L);
            }
            case 31 -> {
                player.closeInventory();
                YapSched.entityLater(plugin, player, () -> {
                    Bukkit.dispatchCommand(player, "yapworld schem undo");
                    YapSched.entityLater(plugin, player, () -> plugin.menus().openSchematics(player), 2L);
                }, 1L);
            }
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
            case 22 -> {
                if (plugin.actions().pluginEnabled("YaPSkills")) {
                    actions.closeAndRun(player, "skills");
                } else {
                    actions.heal(player, player);
                }
            }
            case 24 -> {
                if (plugin.actions().pluginEnabled("YaPLeveledMobs")) {
                    plugin.menus().openLeveledMobs(player);
                } else {
                    actions.closeAndRun(player, "prayer list");
                }
            }
            default -> {
            }
        }
    }

    void handleLeveledMobs(Player player, int slot, boolean shift) {
        if (slot == AdminMenus.SLOT_BACK) {
            plugin.menus().openServerOps(player);
            return;
        }
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        var pl = org.bukkit.Bukkit.getPluginManager().getPlugin("YaPLeveledMobs");
        if (pl == null || !pl.isEnabled()) {
            player.sendMessage("§cYaPLeveledMobs is not loaded.");
            return;
        }
        try {
            Object cfg = pl.getClass().getMethod("leveledConfig").invoke(pl);
            switch (slot) {
                case 19 -> {
                    boolean on = (Boolean) cfg.getClass().getMethod("enabled").invoke(cfg);
                    cfg.getClass().getMethod("setEnabled", boolean.class).invoke(cfg, !on);
                    pl.getClass().getMethod("reloadLeveled").invoke(pl);
                }
                case 20 -> {
                    cfg.getClass().getMethod("cycleStrategy").invoke(cfg);
                    pl.getClass().getMethod("reloadLeveled").invoke(pl);
                }
                case 21 -> {
                    int min = (Integer) cfg.getClass().getMethod("minLevel").invoke(cfg);
                    cfg.getClass().getMethod("setMinLevel", int.class).invoke(cfg, Math.max(1, min - 5));
                    pl.getClass().getMethod("reloadLeveled").invoke(pl);
                }
                case 22 -> {
                    int max = (Integer) cfg.getClass().getMethod("maxLevel").invoke(cfg);
                    cfg.getClass().getMethod("setMaxLevel", int.class).invoke(cfg, max + 5);
                    pl.getClass().getMethod("reloadLeveled").invoke(pl);
                }
                case 23 -> {
                    double b = (Double) cfg.getClass().getMethod("blocksPerLevel").invoke(cfg);
                    double next = shift ? b + 10.0 : Math.max(1.0, b - 10.0);
                    cfg.getClass().getMethod("setBlocksPerLevel", double.class).invoke(cfg, next);
                    pl.getClass().getMethod("reloadLeveled").invoke(pl);
                }
                case 24 -> {
                    boolean nt = (Boolean) cfg.getClass().getMethod("nametagEnabled").invoke(cfg);
                    cfg.getClass().getMethod("setNametagEnabled", boolean.class).invoke(cfg, !nt);
                    pl.getClass().getMethod("reloadLeveled").invoke(pl);
                }
                case 25 -> pl.getClass().getMethod("reloadLeveled").invoke(pl);
                case 31 -> {
                    plugin.actions().closeAndRun(player, "yaplevel info");
                    return;
                }
                default -> {
                    return;
                }
            }
            plugin.menus().openLeveledMobs(player);
        } catch (ReflectiveOperationException e) {
            player.sendMessage("§cLeveled mobs control failed: " + e.getMessage());
        }
    }
}
