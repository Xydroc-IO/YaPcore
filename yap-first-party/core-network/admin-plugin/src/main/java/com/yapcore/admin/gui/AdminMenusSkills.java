package com.yapcore.admin.gui;

import com.yapcore.admin.AdminPlugin;
import com.yapcore.admin.action.AdminSkillBridge;
import com.yapcore.admin.session.AdminSession;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;

/** Skills give-XP / set-level menus. */
final class AdminMenusSkills {

    private final AdminPlugin plugin;

    AdminMenusSkills(AdminPlugin plugin) {
        this.plugin = plugin;
    }

    void openSkillPick(Player player, Player target, boolean forXp) {
        AdminSession session = plugin.session(player.getUniqueId());
        session.setTarget(target.getUniqueId(), target.getName());
        session.setPickForSkillXp(forXp);
        session.setPickForSkillLevel(!forXp);
        AdminMenuHolder holder = new AdminMenuHolder(
                AdminMenuKind.SKILL_PICK, target.getUniqueId(), target.getName());
        String title = (forXp ? "Give XP: " : "Set level: ") + target.getName();
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text(title, NamedTextColor.GREEN));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        inv.setItem(AdminMenuSlots.SLOT_INFO, AdminMenuHolder.icon(Material.EXPERIENCE_BOTTLE,
                forXp ? "Pick a skill (XP)" : "Pick a skill (level)",
                "Target: " + target.getName()));
        List<AdminSkillBridge.SkillRow> skills = AdminSkillBridge.skills();
        if (skills.isEmpty()) {
            inv.setItem(22, AdminMenuHolder.icon(Material.BARRIER, NamedTextColor.RED, "No skills",
                    "YaPSkills not loaded or empty"));
        } else {
            int slot = 10;
            for (AdminSkillBridge.SkillRow row : skills) {
                if (slot == 17 || slot == 26 || slot == 35) {
                    slot++;
                }
                if (slot >= 44) {
                    break;
                }
                String state = row.enabled() ? "Enabled" : "Disabled";
                inv.setItem(slot, AdminMenuHolder.icon(row.icon(), NamedTextColor.YELLOW, row.display(),
                        "id: " + row.id(),
                        state,
                        forXp ? "Click → choose XP amount" : "Click → choose level"));
                slot++;
            }
        }
        inv.setItem(AdminMenuSlots.SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(AdminMenuSlots.SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    void openXpAmount(Player player, Player target, String skillId) {
        AdminSession session = plugin.session(player.getUniqueId());
        session.setTarget(target.getUniqueId(), target.getName());
        session.setSkillId(skillId);
        AdminMenuHolder holder = new AdminMenuHolder(
                AdminMenuKind.SKILL_XP_AMOUNT, target.getUniqueId(), target.getName());
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("XP → " + skillId, NamedTextColor.AQUA));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        inv.setItem(AdminMenuSlots.SLOT_INFO, AdminMenuHolder.icon(Material.EXPERIENCE_BOTTLE, "Give XP",
                "Skill: " + skillId,
                "Player: " + target.getName()));
        inv.setItem(19, AdminMenuHolder.icon(Material.GREEN_DYE, "100 XP", "Small boost"));
        inv.setItem(20, AdminMenuHolder.icon(Material.LIME_DYE, "500 XP", "Medium boost"));
        inv.setItem(21, AdminMenuHolder.icon(Material.YELLOW_DYE, "1,000 XP", "Large boost"));
        inv.setItem(22, AdminMenuHolder.icon(Material.ORANGE_DYE, "5,000 XP", "Huge boost"));
        inv.setItem(23, AdminMenuHolder.icon(Material.RED_DYE, "25,000 XP", "Massive boost"));
        inv.setItem(25, AdminMenuHolder.icon(Material.NETHER_STAR, NamedTextColor.GOLD, "100,000 XP",
                "Near-max dump"));
        inv.setItem(AdminMenuSlots.SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(AdminMenuSlots.SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    void openSetLevel(Player player, Player target, String skillId) {
        AdminSession session = plugin.session(player.getUniqueId());
        session.setTarget(target.getUniqueId(), target.getName());
        session.setSkillId(skillId);
        int max = AdminSkillBridge.maxLevel();
        AdminMenuHolder holder = new AdminMenuHolder(
                AdminMenuKind.SKILL_SET_LEVEL, target.getUniqueId(), target.getName());
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("Level → " + skillId, NamedTextColor.GOLD));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        inv.setItem(AdminMenuSlots.SLOT_INFO, AdminMenuHolder.icon(Material.DIAMOND, "Set level",
                "Skill: " + skillId,
                "Player: " + target.getName(),
                "Max: " + max));
        inv.setItem(19, AdminMenuHolder.icon(Material.IRON_NUGGET, "Level 1", "Reset"));
        inv.setItem(20, AdminMenuHolder.icon(Material.IRON_INGOT, "Level 10", ""));
        inv.setItem(21, AdminMenuHolder.icon(Material.GOLD_INGOT, "Level 25", ""));
        inv.setItem(22, AdminMenuHolder.icon(Material.DIAMOND, "Level 50", ""));
        inv.setItem(23, AdminMenuHolder.icon(Material.EMERALD, "Level 75", ""));
        inv.setItem(24, AdminMenuHolder.icon(Material.NETHERITE_INGOT, "Level 100", ""));
        inv.setItem(25, AdminMenuHolder.icon(Material.NETHER_STAR, NamedTextColor.GOLD, "Max (" + max + ")",
                "Cap level"));
        inv.setItem(AdminMenuSlots.SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(AdminMenuSlots.SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }
}
