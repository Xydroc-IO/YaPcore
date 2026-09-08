package com.yapcore.admin.gui;

import com.yapcore.admin.AdminPlugin;
import com.yapcore.admin.session.AdminSession;
import com.yapcore.admin.session.ItemCreateDraft;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Click handlers for custom items hub, browse, manage, and cooldowns. */
final class AdminMenuClickCustomItemsBrowse {

    private final AdminPlugin plugin;

    AdminMenuClickCustomItemsBrowse(AdminPlugin plugin) {
        this.plugin = plugin;
    }

    void handleCustomItems(Player player, int slot) {
        if (slot == AdminMenus.SLOT_BACK) {
            plugin.menus().openHub(player);
            return;
        }
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        AdminSession session = plugin.session(player.getUniqueId());
        switch (slot) {
            case 20 -> {
                session.setMaterialPage(0);
                plugin.menus().openCustomItemsBrowse(player);
            }
            case 22 -> plugin.menus().openCustomItemsCreate(player);
            case 24 -> {
                session.setMaterialPage(0);
                plugin.menus().openCustomItemsCooldownBrowse(player);
            }
            case 29 -> plugin.actions().closeAndRun(player, "yapitems reload");
            case 31 -> {
                session.cycleGiveAmount();
                plugin.menus().openCustomItemsHub(player);
            }
            default -> {
            }
        }
    }

    void handleCustomItemsCooldown(Player player, int slot, ItemStack clicked) {
        AdminSession session = plugin.session(player.getUniqueId());
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == AdminMenus.MAT_BACK) {
            plugin.menus().openCustomItemsHub(player);
            return;
        }
        if (slot == AdminMenus.MAT_PREV) {
            session.setMaterialPage(Math.max(0, session.materialPage() - 1));
            plugin.menus().openCustomItemsCooldownBrowse(player);
            return;
        }
        if (slot == AdminMenus.MAT_NEXT) {
            session.setMaterialPage(session.materialPage() + 1);
            plugin.menus().openCustomItemsCooldownBrowse(player);
            return;
        }
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }
        String id = AdminMenuItemSupport.plainName(clicked);
        if (id.isBlank()) {
            return;
        }
        session.setCustomItemId(id);
        plugin.menus().openCustomItemsCooldownEdit(player);
    }

    void handleCustomItemsCooldownEdit(Player player, int slot, ItemStack clicked) {
        AdminSession session = plugin.session(player.getUniqueId());
        if (slot == AdminMenus.SLOT_BACK) {
            plugin.menus().openCustomItemsCooldownBrowse(player);
            return;
        }
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == 40) {
            String id = session.customItemId();
            if (id.isBlank()) {
                return;
            }
            session.setPendingAbilityCooldownChat(true);
            player.closeInventory();
            player.sendMessage("§eType ability cooldown for §f" + id + "§e (e.g. §f4s§e / §f2.5s§e / §f500ms§e).");
            player.sendMessage("§7Or type §fcancel§7 to abort.");
            return;
        }
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }
        String preset = AdminMenuItemSupport.plainName(clicked).replace("▶ ", "").trim();
        if (!preset.matches("\\d+(\\.\\d+)?s")) {
            return;
        }
        String id = session.customItemId();
        if (id.isBlank()) {
            return;
        }
        if (plugin.actions().setItemAbilityCooldown(player, id, preset)) {
            plugin.menus().openCustomItemsCooldownEdit(player);
        }
    }


    void handleCustomItemsBrowse(Player player, int slot, ItemStack clicked, boolean shift) {
        AdminSession session = plugin.session(player.getUniqueId());
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == AdminMenus.MAT_BACK) {
            plugin.menus().openCustomItemsHub(player);
            return;
        }
        if (slot == AdminMenus.MAT_PREV) {
            session.setMaterialPage(Math.max(0, session.materialPage() - 1));
            plugin.menus().openCustomItemsBrowse(player);
            return;
        }
        if (slot == AdminMenus.MAT_NEXT) {
            session.setMaterialPage(session.materialPage() + 1);
            plugin.menus().openCustomItemsBrowse(player);
            return;
        }
        if (slot == AdminMenus.MAT_AMOUNT) {
            session.cycleGiveAmount();
            plugin.menus().openCustomItemsBrowse(player);
            return;
        }
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }
        String id = AdminMenuItemSupport.plainName(clicked);
        if (id.isBlank()) {
            return;
        }
        if (shift) {
            int amount = 64;
            String who = session.hasTarget() ? session.targetName() : player.getName();
            plugin.actions().closeAndRun(player, "yapitems give " + id + " " + amount + " " + who);
            return;
        }
        session.setCustomItemId(id);
        plugin.menus().openCustomItemsManage(player);
    }

    void handleCustomItemsManage(Player player, int slot) {
        AdminSession session = plugin.session(player.getUniqueId());
        String id = session.customItemId();
        if (slot == AdminMenus.SLOT_BACK) {
            plugin.menus().openCustomItemsBrowse(player);
            return;
        }
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        switch (slot) {
            case 20 -> {
                if (id.isBlank()) {
                    return;
                }
                String who = session.hasTarget() ? session.targetName() : player.getName();
                plugin.actions().closeAndRun(player, "yapitems give " + id + " " + session.giveAmount() + " " + who);
            }
            case 22 -> {
                if (id.isBlank()) {
                    return;
                }
                ItemCreateDraft draft = session.itemCreate();
                if (!draft.loadFromCustomFile(id)) {
                    player.sendMessage("§cCannot edit §f" + id + "§c — only items under §fitems/custom/§c.");
                    return;
                }
                plugin.menus().openCustomItemsCreateBuild(player);
            }
            case 24 -> {
                if (id.isBlank()) {
                    return;
                }
                plugin.menus().openCustomItemsCooldownEdit(player);
            }
            case 31 -> {
                if (id.isBlank()) {
                    return;
                }
                plugin.actions().closeAndRun(player, "yapitems delete " + id);
            }
            default -> {
            }
        }
    }
}
