package com.yapcore.admin.gui;

import com.yapcore.admin.AdminPlugin;
import com.yapcore.admin.action.AdminSkillBridge;
import com.yapcore.admin.session.AdminSession;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Click handlers for skill give-XP / set-level menus. */
final class AdminMenuClickSkills {

    private final AdminPlugin plugin;

    AdminMenuClickSkills(AdminPlugin plugin) {
        this.plugin = plugin;
    }

    void handleSkillPick(Player player, AdminMenuHolder holder, int slot, ItemStack clicked) {
        if (slot == AdminMenus.SLOT_BACK) {
            Player target = resolveTarget(holder);
            if (target != null) {
                plugin.menus().openPlayerActions(player, target);
            } else {
                plugin.menus().openCombatSkills(player);
            }
            return;
        }
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (clicked == null || clicked.getType() == Material.AIR || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) {
            return;
        }
        String skillId = skillIdFromItem(clicked);
        if (skillId.isBlank()) {
            return;
        }
        Player target = resolveTarget(holder);
        if (target == null || !target.isOnline()) {
            player.sendMessage("§cPlayer is offline.");
            return;
        }
        AdminSession session = plugin.session(player.getUniqueId());
        session.setSkillId(skillId);
        if (session.pickForSkillLevel()) {
            plugin.menus().openSkillSetLevel(player, target, skillId);
        } else {
            plugin.menus().openSkillXpAmount(player, target, skillId);
        }
    }

    void handleXpAmount(Player player, AdminMenuHolder holder, int slot) {
        if (slot == AdminMenus.SLOT_BACK) {
            Player target = resolveTarget(holder);
            AdminSession session = plugin.session(player.getUniqueId());
            if (target != null) {
                plugin.menus().openSkillPick(player, target, true);
            } else {
                plugin.menus().openCombatSkills(player);
            }
            session.setPickForSkillXp(true);
            return;
        }
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        double amount = switch (slot) {
            case 19 -> 100;
            case 20 -> 500;
            case 21 -> 1_000;
            case 22 -> 5_000;
            case 23 -> 25_000;
            case 25 -> 100_000;
            default -> -1;
        };
        if (amount < 0) {
            return;
        }
        Player target = resolveTarget(holder);
        String skillId = plugin.session(player.getUniqueId()).skillId();
        if (target == null || !target.isOnline()) {
            player.sendMessage("§cPlayer is offline.");
            return;
        }
        if (skillId.isBlank()) {
            player.sendMessage("§cNo skill selected.");
            return;
        }
        AdminSkillBridge.addXp(player, target, skillId, amount);
    }

    void handleSetLevel(Player player, AdminMenuHolder holder, int slot) {
        if (slot == AdminMenus.SLOT_BACK) {
            Player target = resolveTarget(holder);
            if (target != null) {
                plugin.menus().openSkillPick(player, target, false);
            } else {
                plugin.menus().openCombatSkills(player);
            }
            plugin.session(player.getUniqueId()).setPickForSkillLevel(true);
            return;
        }
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        int max = AdminSkillBridge.maxLevel();
        int level = switch (slot) {
            case 19 -> 1;
            case 20 -> 10;
            case 21 -> 25;
            case 22 -> 50;
            case 23 -> 75;
            case 24 -> 100;
            case 25 -> max;
            default -> -1;
        };
        if (level < 1) {
            return;
        }
        Player target = resolveTarget(holder);
        String skillId = plugin.session(player.getUniqueId()).skillId();
        if (target == null || !target.isOnline()) {
            player.sendMessage("§cPlayer is offline.");
            return;
        }
        if (skillId.isBlank()) {
            player.sendMessage("§cNo skill selected.");
            return;
        }
        AdminSkillBridge.setLevel(player, target, skillId, level);
    }

    private Player resolveTarget(AdminMenuHolder holder) {
        if (holder.targetUuid() == null) {
            return null;
        }
        return Bukkit.getPlayer(holder.targetUuid());
    }

    private static String skillIdFromItem(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || meta.lore() == null) {
            return "";
        }
        for (var line : meta.lore()) {
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(line).trim();
            if (plain.toLowerCase(java.util.Locale.ROOT).startsWith("id:")) {
                return plain.substring(3).trim().toLowerCase(java.util.Locale.ROOT);
            }
        }
        return "";
    }
}
