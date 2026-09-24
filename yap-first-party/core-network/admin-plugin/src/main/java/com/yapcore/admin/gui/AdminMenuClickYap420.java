package com.yapcore.admin.gui;

import com.yapcore.admin.AdminPlugin;
import com.yapcore.admin.session.AdminSession;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Click handlers for YaP420 admin hub and give browser. */
final class AdminMenuClickYap420 {

    private final AdminPlugin plugin;

    AdminMenuClickYap420(AdminPlugin plugin) {
        this.plugin = plugin;
    }

    void handleHub(Player player, int slot) {
        if (slot == AdminMenus.SLOT_BACK) {
            plugin.menus().openHub(player);
            return;
        }
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (!plugin.actions().pluginEnabled("YaP420")) {
            player.sendMessage("§cYaP420 is not loaded.");
            return;
        }
        if (!player.hasPermission("yap420.admin") && !player.hasPermission("yapadmin.give") && !player.isOp()) {
            player.sendMessage("§cNeed §fyap420.admin §cor §fyapadmin.give§c.");
            return;
        }
        AdminSession session = plugin.session(player.getUniqueId());
        String who = AdminMenusYap420.giveTargetName(player, session);
        int amount = session.giveAmount();
        switch (slot) {
            case AdminMenuSlots.Y420_AMOUNT -> {
                session.cycleGiveAmount();
                plugin.menus().openYap420Hub(player);
            }
            case AdminMenuSlots.Y420_BROWSE -> plugin.menus().openYap420Give(player);
            case AdminMenuSlots.Y420_GIVE_SEEDS_SATIVA -> {
                giveAll(player, who, amount, "yap420_seed_sativa");
                plugin.menus().openYap420Hub(player);
            }
            case AdminMenuSlots.Y420_GIVE_SEEDS_INDICA -> {
                giveAll(player, who, amount, "yap420_seed_indica");
                plugin.menus().openYap420Hub(player);
            }
            case AdminMenuSlots.Y420_GIVE_BUDS -> {
                giveAll(player, who, amount, "yap420_bud_cured_sativa", "yap420_bud_cured_indica");
                plugin.menus().openYap420Hub(player);
            }
            case AdminMenuSlots.Y420_GIVE_CONSUME -> {
                giveAll(player, who, amount,
                        "yap420_rolling_paper",
                        "yap420_joint_sativa",
                        "yap420_joint_indica",
                        "yap420_brownie");
                plugin.menus().openYap420Hub(player);
            }
            case AdminMenuSlots.Y420_GIVE_RACK -> {
                giveAll(player, who, 1, "yap420_drying_rack");
                plugin.menus().openYap420Hub(player);
            }
            case AdminMenuSlots.Y420_GIVE_ALL -> {
                giveAll(player, who, amount,
                        "yap420_seed_sativa",
                        "yap420_seed_indica",
                        "yap420_rolling_paper",
                        "yap420_bud_cured_sativa",
                        "yap420_bud_cured_indica",
                        "yap420_joint_sativa",
                        "yap420_joint_indica",
                        "yap420_drying_rack");
                plugin.menus().openYap420Hub(player);
            }
            case AdminMenuSlots.Y420_STARTER -> {
                giveAll(player, who, 16, "yap420_seed_sativa", "yap420_seed_indica", "yap420_rolling_paper");
                giveAll(player, who, 1, "yap420_drying_rack");
                giveAll(player, who, 2, "yap420_joint_sativa", "yap420_joint_indica");
                plugin.menus().openYap420Hub(player);
            }
            case AdminMenuSlots.Y420_RELOAD -> plugin.actions().closeAndRun(player, "yap420 reload");
            case AdminMenuSlots.Y420_REMOVE -> plugin.actions().closeAndRun(player, "yap420 remove");
            case AdminMenuSlots.Y420_INFO -> plugin.actions().closeAndRun(player, "yap420 info");
            default -> {
            }
        }
    }

    void handleGive(Player player, int slot, ItemStack clicked) {
        if (slot == AdminMenus.SLOT_BACK) {
            plugin.menus().openYap420Hub(player);
            return;
        }
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        AdminSession session = plugin.session(player.getUniqueId());
        if (slot == AdminMenuSlots.Y420_AMOUNT) {
            session.cycleGiveAmount();
            plugin.menus().openYap420Give(player);
            return;
        }
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }
        String id = idFromIcon(clicked);
        if (id == null || id.isBlank()) {
            return;
        }
        String who = AdminMenusYap420.giveTargetName(player, session);
        plugin.actions().giveYapItem(player, who, id, session.giveAmount());
        plugin.menus().openYap420Give(player);
    }

    private void giveAll(Player admin, String targetName, int amount, String... ids) {
        for (String id : ids) {
            plugin.actions().giveYapItem(admin, targetName, id, amount);
        }
    }

    private static String idFromIcon(ItemStack clicked) {
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return null;
        }
        List<net.kyori.adventure.text.Component> lore = meta.lore();
        if (lore == null) {
            return null;
        }
        for (net.kyori.adventure.text.Component line : lore) {
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(line);
            if (plain.startsWith("id:")) {
                return plain.substring(3).trim();
            }
        }
        return null;
    }
}
