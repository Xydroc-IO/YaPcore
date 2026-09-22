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
        if (plugin.actions().pluginEnabled("YaPEssentials")) {
            boolean keep = keepInventoryEnabled();
            inv.setItem(32, AdminMenuHolder.icon(
                    keep ? Material.CHEST : Material.DROPPER,
                    keep ? "Keep inventory: ON" : "Keep inventory: OFF",
                    keep ? "Players keep items on death" : "Players drop items on death",
                    "Click to toggle · /yapess keepinventory"));
        }
        if (plugin.actions().pluginEnabled("YaPLeveledMobs")) {
            inv.setItem(34, AdminMenuHolder.icon(Material.ZOMBIE_HEAD, "Leveled mobs",
                    "Enable · strategy · levels", "/yaplevel"));
        }
        if (plugin.actions().pluginEnabled("YaP-QoL")) {
            inv.setItem(31, AdminMenuHolder.icon(Material.GOLDEN_AXE, "QoL tools",
                    "Timber axe · excavator give",
                    "Toggle features · /yapqol"));
        }
        if (plugin.actions().pluginEnabled("YaPProtect")) {
            inv.setItem(29, AdminMenuHolder.icon(Material.RECOVERY_COMPASS, NamedTextColor.GOLD, "Rollback…",
                    "Undo logged block changes",
                    "5m · 15m · 30m · 1h · 8h · 24h",
                    "This world · YaPProtect"));
        }
        inv.setItem(AdminMenuSlots.SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(AdminMenuSlots.SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    /** Time-window rollback via YaPProtect for the player's current world. */
    void openServerRollback(Player player) {
        plugin.session(player.getUniqueId()).clearPendingRollback();
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.SERVER_ROLLBACK);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("Rollback", NamedTextColor.GOLD));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);

        String world = player.getWorld().getName();
        boolean protectOn = plugin.actions().pluginEnabled("YaPProtect");
        boolean canRollback = player.hasPermission("yapprotect.rollback");
        if (!protectOn) {
            inv.setItem(AdminMenuSlots.SLOT_INFO, AdminMenuHolder.icon(Material.BARRIER, NamedTextColor.RED,
                    "YaPProtect offline",
                    "Enable YaPProtect to roll back"));
        } else if (!canRollback) {
            inv.setItem(AdminMenuSlots.SLOT_INFO, AdminMenuHolder.icon(Material.BARRIER, NamedTextColor.RED,
                    "No permission",
                    "Need yapprotect.rollback"));
        } else {
            inv.setItem(AdminMenuSlots.SLOT_INFO, AdminMenuHolder.icon(Material.RECOVERY_COMPASS,
                    NamedTextColor.GOLD, "Rollback this world",
                    "World: " + world,
                    "Restores logged blocks / chests / explosions",
                    "Click a duration, then click again to confirm"));
        }

        putRollbackDuration(inv, 19, Material.LIME_CONCRETE, NamedTextColor.GREEN, "5 minutes", "5m",
                protectOn && canRollback);
        putRollbackDuration(inv, 20, Material.LIME_CONCRETE, NamedTextColor.GREEN, "15 minutes", "15m",
                protectOn && canRollback);
        putRollbackDuration(inv, 21, Material.YELLOW_CONCRETE, NamedTextColor.YELLOW, "30 minutes", "30m",
                protectOn && canRollback);
        putRollbackDuration(inv, 22, Material.ORANGE_CONCRETE, NamedTextColor.GOLD, "1 hour", "1h",
                protectOn && canRollback);
        putRollbackDuration(inv, 23, Material.RED_CONCRETE, NamedTextColor.RED, "8 hours", "8h",
                protectOn && canRollback);
        putRollbackDuration(inv, 24, Material.RED_CONCRETE, NamedTextColor.DARK_RED, "24 hours", "24h",
                protectOn && canRollback);

        inv.setItem(AdminMenuSlots.SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(AdminMenuSlots.SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    private static void putRollbackDuration(Inventory inv, int slot, Material mat, NamedTextColor color,
                                            String label, String duration, boolean enabled) {
        if (enabled) {
            inv.setItem(slot, AdminMenuHolder.icon(mat, color, label,
                    "Roll back last " + label.toLowerCase(Locale.ROOT),
                    "Click twice to confirm · " + duration));
        } else {
            inv.setItem(slot, AdminMenuHolder.icon(Material.GRAY_CONCRETE, label,
                    "Unavailable"));
        }
    }

    private static boolean keepInventoryEnabled() {
        var ess = Bukkit.getPluginManager().getPlugin("YaPEssentials");
        if (ess == null || !ess.isEnabled()) {
            return false;
        }
        try {
            Object cfg = ess.getClass().getMethod("essentialsConfig").invoke(ess);
            Object v = cfg.getClass().getMethod("keepInventory").invoke(cfg);
            return v instanceof Boolean b && b;
        } catch (ReflectiveOperationException e) {
            return false;
        }
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
            inv.setItem(20, AdminMenuHolder.icon(Material.WOODEN_AXE, NamedTextColor.AQUA, "World tools",
                    "World edit · schematics · paste preview",
                    "Same panel as the hub tile"));
        }
        if (plugin.actions().pluginEnabled("YaPStacker")) {
            inv.setItem(21, AdminMenuHolder.icon(Material.SPAWNER, "Stacker", "Open stacker GUI"));
        }
        if (plugin.actions().pluginEnabled("YaPPlayerData")) {
            inv.setItem(22, AdminMenuHolder.icon(Material.COMPASS, "Player menu", "Open /menu"));
        }
        if (plugin.actions().pluginEnabled("YaPPregen")) {
            inv.setItem(23, AdminMenuHolder.icon(Material.RECOVERY_COMPASS, "Pregen", "Show pregen status"));
        }
        if (plugin.actions().pluginEnabled("YaPLeveledMobs")) {
            inv.setItem(24, AdminMenuHolder.icon(Material.ZOMBIE_HEAD, "Leveled mobs",
                    "Enable · strategy · levels"));
        }
        if (plugin.actions().pluginEnabled("YaP-QoL")) {
            inv.setItem(25, AdminMenuHolder.icon(Material.GOLDEN_AXE, "QoL tools",
                    "Timber axe · excavator",
                    "Toggle + give VIP tools"));
        }
        inv.setItem(28, AdminMenuHolder.icon(Material.KELP, NamedTextColor.DARK_GREEN, "YaP420",
                "Plant · cure · give · reload",
                plugin.actions().pluginEnabled("YaP420")
                        ? "YaP420 online"
                        : "YaP420 offline — open for status"));
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
                "Marathon · swimming · mining · more"));
        if (plugin.actions().pluginEnabled("YaPSkills")) {
            inv.setItem(19, AdminMenuHolder.icon(Material.EXPERIENCE_BOTTLE, NamedTextColor.YELLOW, "Skills menu",
                    "Open /skills"));
            inv.setItem(21, AdminMenuHolder.icon(Material.LIME_DYE, NamedTextColor.GREEN, "★ Give XP…",
                    "Pick a player → skill → amount",
                    "Marathon, swimming, mining, …"));
            inv.setItem(23, AdminMenuHolder.icon(Material.DIAMOND, NamedTextColor.AQUA, "★ Set level…",
                    "Pick a player → skill → level",
                    "1 · 10 · 25 · 50 · 75 · 100 · max"));
        } else {
            inv.setItem(22, AdminMenuHolder.icon(Material.BARRIER, NamedTextColor.RED, "YaPSkills offline",
                    "Enable the skills plugin first"));
        }
        if (plugin.actions().pluginEnabled("YaPLeveledMobs")
                || plugin.actions().pluginEnabled("YaPMobs")) {
            inv.setItem(25, AdminMenuHolder.icon(Material.ZOMBIE_HEAD, "Leveled mobs",
                    "Enable · strategy · levels"));
        }
        inv.setItem(AdminMenuSlots.SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(AdminMenuSlots.SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }
}
