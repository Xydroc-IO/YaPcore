package com.yapcore.admin.gui;

import com.yapcore.admin.AdminPlugin;
import com.yapcore.admin.session.AdminSession;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.Locale;

/** Server ops, economy, deep links, and combat skills menus. */
final class AdminMenusOps {

    private final AdminPlugin plugin;

    AdminMenusOps(AdminPlugin plugin) {
        this.plugin = plugin;
    }

    void openServerOps(Player player) {
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.SERVER_OPS);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Server ops", NamedTextColor.GOLD));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);

        double[] tps;
        try {
            tps = Bukkit.getTPS();
        } catch (Throwable t) {
            tps = new double[0];
        }
        String tpsLine = tps.length > 0 ? String.format(Locale.ROOT, "%.2f", tps[0]) : "n/a";
        inv.setItem(AdminMenuSlots.SLOT_INFO, AdminMenuHolder.icon(Material.COMMAND_BLOCK, "Server status",
                "Online: " + Bukkit.getOnlinePlayers().size(),
                "Worlds: " + Bukkit.getWorlds().size(),
                "TPS (1m): " + tpsLine));

        List<String> presets = plugin.adminConfig().broadcastPresets();
        int slot = 19;
        for (int i = 0; i < presets.size() && slot < 26; i++) {
            String msg = presets.get(i);
            String shortMsg = msg.length() > 40 ? msg.substring(0, 37) + "…" : msg;
            inv.setItem(slot++, AdminMenuHolder.icon(Material.NOTE_BLOCK, "Broadcast #" + (i + 1),
                    shortMsg));
        }
        inv.setItem(28, AdminMenuHolder.icon(Material.BOOKSHELF, "Open Ranks GUI",
                "YaPPerms reload lives there"));
        if (plugin.actions().pluginEnabled("YaPEssentials") || plugin.actions().pluginEnabled("YaPDisasters")) {
            inv.setItem(30, AdminMenuHolder.icon(Material.WATER_BUCKET, "Weather / Disasters",
                    "Clear · storms · disasters", "/yapdisaster"));
        }
        inv.setItem(AdminMenuSlots.SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(AdminMenuSlots.SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    void openEconomy(Player player) {
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.ECONOMY);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Economy", NamedTextColor.GOLD));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        AdminSession session = plugin.session(player.getUniqueId());
        String target = session.hasTarget() ? session.targetName() : player.getName() + " (self)";
        inv.setItem(AdminMenuSlots.SLOT_INFO, AdminMenuHolder.icon(Material.GOLD_INGOT, "Grant money",
                "Target: " + target,
                "Uses /eco give"));
        int slot = 19;
        for (int amount : plugin.adminConfig().moneyAmounts()) {
            inv.setItem(slot++, AdminMenuHolder.icon(Material.EMERALD, "+" + amount,
                    "Give " + amount + " to " + target));
        }
        inv.setItem(AdminMenuSlots.SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(AdminMenuSlots.SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    void openDeepLinks(Player player) {
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.DEEP_LINKS);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("More", NamedTextColor.AQUA));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        inv.setItem(AdminMenuSlots.SLOT_INFO, AdminMenuHolder.icon(Material.COMPASS, "More",
                "Opens another in-game menu"));
        if (plugin.actions().pluginEnabled("YaPPerms")) {
            inv.setItem(19, AdminMenuHolder.icon(Material.NAME_TAG, "Ranks", "Open YaPPerms"));
        }
        if (plugin.actions().pluginEnabled("YaPWorld")) {
            inv.setItem(20, AdminMenuHolder.icon(Material.WOODEN_AXE, "World edit", "Open YaPWorld panel"));
            inv.setItem(23, AdminMenuHolder.icon(Material.MAP, "Schematics", "Browse saved schematics"));
        }
        if (plugin.actions().pluginEnabled("YaPStacker")) {
            inv.setItem(21, AdminMenuHolder.icon(Material.SPAWNER, "Stacker", "Open stacker GUI"));
        }
        if (plugin.actions().pluginEnabled("YaPPlayerData")) {
            inv.setItem(22, AdminMenuHolder.icon(Material.COMPASS, "Player menu", "Open /menu"));
        }
        if (plugin.actions().pluginEnabled("YaPPregen")) {
            inv.setItem(24, AdminMenuHolder.icon(Material.RECOVERY_COMPASS, "Pregen", "Show pregen status"));
        }
        inv.setItem(AdminMenuSlots.SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(AdminMenuSlots.SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }
    void openCombatSkills(Player player) {
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.COMBAT_SKILLS);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Skills", NamedTextColor.GREEN));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        inv.setItem(AdminMenuSlots.SLOT_INFO, AdminMenuHolder.icon(Material.EXPERIENCE_BOTTLE, "Skills",
                "Mining · woodcutting · strength"));
        if (plugin.actions().pluginEnabled("YaPSkills")) {
            inv.setItem(22, AdminMenuHolder.icon(Material.EXPERIENCE_BOTTLE, "Skills menu", "/skills"));
        }
        inv.setItem(AdminMenuSlots.SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(AdminMenuSlots.SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }
}
