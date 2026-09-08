package com.yapcore.admin.gui;

import com.yapcore.admin.AdminConfig;
import com.yapcore.admin.AdminPlugin;
import com.yapcore.admin.session.AdminSession;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Click handlers for give hub, presets, kits, and materials. */
final class AdminMenuClickGive {

    private final AdminPlugin plugin;

    AdminMenuClickGive(AdminPlugin plugin) {
        this.plugin = plugin;
    }

    void handleGiveHub(Player player, int slot) {
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
            case AdminMenus.GIVE_PRESETS -> plugin.menus().openGivePresets(player);
            case AdminMenus.GIVE_KITS -> plugin.menus().openGiveKits(player);
            case AdminMenus.GIVE_MATS -> plugin.menus().openGiveMaterials(player);
            case AdminMenus.GIVE_AMOUNT -> {
                session.cycleGiveAmount();
                plugin.menus().openGiveHub(player);
            }
            case AdminMenus.GIVE_TARGET -> plugin.menus().openPlayers(player);
            default -> {
            }
        }
    }

    void handleGivePresets(Player player, int slot, ItemStack clicked, boolean shift) {
        if (slot == AdminMenus.SLOT_BACK) {
            plugin.menus().openGiveHub(player);
            return;
        }
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (clicked == null || clicked.getType().isAir() || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) {
            return;
        }
        String name = AdminMenuItemSupport.plainName(clicked);
        AdminConfig.ItemPreset match = null;
        for (AdminConfig.ItemPreset preset : plugin.adminConfig().presets()) {
            if (preset.displayName().equalsIgnoreCase(name) || preset.material() == clicked.getType()) {
                match = preset;
                break;
            }
        }
        if (match == null) {
            return;
        }
        int amount = shift ? match.amount() * 4 : match.amount();
        Player target = plugin.actions().resolveGiveTarget(player);
        plugin.actions().giveItem(player, target, match.material(), amount);
    }

    void handleGiveKits(Player player, int slot, ItemStack clicked) {
        if (slot == AdminMenus.SLOT_BACK) {
            plugin.menus().openGiveHub(player);
            return;
        }
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (clicked == null || clicked.getType() != Material.CHEST) {
            return;
        }
        String kit = AdminMenuItemSupport.plainName(clicked);
        if (kit.isBlank() || "Kits".equalsIgnoreCase(kit)) {
            return;
        }
        Player target = plugin.actions().resolveGiveTarget(player);
        plugin.actions().giveKit(player, target, kit);
    }

    void handleGiveMaterials(Player player, int slot, ItemStack clicked) {
        AdminSession session = plugin.session(player.getUniqueId());
        if (slot == AdminMenus.MAT_BACK) {
            plugin.menus().openGiveHub(player);
            return;
        }
        if (slot == AdminMenus.MAT_AMOUNT) {
            session.cycleGiveAmount();
            plugin.menus().openGiveMaterials(player);
            return;
        }
        if (slot == AdminMenus.MAT_PREV) {
            session.setMaterialPage(session.materialPage() - 1);
            plugin.menus().openGiveMaterials(player);
            return;
        }
        if (slot == AdminMenus.MAT_NEXT) {
            session.setMaterialPage(session.materialPage() + 1);
            plugin.menus().openGiveMaterials(player);
            return;
        }
        if (slot == AdminMenus.CAT_ALL) {
            session.setCategory(AdminSession.MaterialCategory.ALL);
            plugin.menus().openGiveMaterials(player);
            return;
        }
        if (slot == AdminMenus.CAT_BLOCKS) {
            session.setCategory(AdminSession.MaterialCategory.BLOCKS);
            plugin.menus().openGiveMaterials(player);
            return;
        }
        if (slot == AdminMenus.CAT_TOOLS) {
            session.setCategory(AdminSession.MaterialCategory.TOOLS);
            plugin.menus().openGiveMaterials(player);
            return;
        }
        if (slot == AdminMenus.CAT_COMBAT) {
            session.setCategory(AdminSession.MaterialCategory.COMBAT);
            plugin.menus().openGiveMaterials(player);
            return;
        }
        if (slot == AdminMenus.CAT_FOOD) {
            session.setCategory(AdminSession.MaterialCategory.FOOD);
            plugin.menus().openGiveMaterials(player);
            return;
        }
        if (slot == AdminMenus.CAT_MISC) {
            session.setCategory(AdminSession.MaterialCategory.MISC);
            plugin.menus().openGiveMaterials(player);
            return;
        }
        if (clicked == null || clicked.getType().isAir() || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) {
            return;
        }
        if (slot < 9 || slot >= 45) {
            return;
        }
        Material mat = clicked.getType();
        if (!mat.isItem()) {
            return;
        }
        Player target = plugin.actions().resolveGiveTarget(player);
        plugin.actions().giveItem(player, target, mat, session.giveAmount());
    }
}
