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
        inv.setItem(AdminMenuSlots.SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(AdminMenuSlots.SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
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
            inv.setItem(20, AdminMenuHolder.icon(Material.WOODEN_AXE, "World edit", "Open YaPWorld panel"));
            inv.setItem(23, AdminMenuHolder.icon(Material.MAP, "Schematics",
                    "Browse · confirm · move preview"));
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
        if (plugin.actions().pluginEnabled("YaPLeveledMobs")) {
            inv.setItem(25, AdminMenuHolder.icon(Material.ZOMBIE_HEAD, "Leveled mobs",
                    "Enable · strategy · levels"));
        }
        inv.setItem(AdminMenuSlots.SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(AdminMenuSlots.SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    /** Dedicated schem browse + preview controls (confirm / move / cancel / undo). */
    void openSchematics(Player player) {
        if (!plugin.actions().pluginEnabled("YaPWorld")) {
            player.sendMessage("§cYaPWorld is not loaded.");
            return;
        }
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.SCHEMATICS);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Schematics", NamedTextColor.AQUA));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        inv.setItem(AdminMenuSlots.SLOT_INFO, AdminMenuHolder.icon(Material.MAP, NamedTextColor.AQUA, "Schematics",
                "1. Browse → click a file (preview outline)",
                "2. Walk to position → Move here",
                "3. Confirm paste to place blocks"));
        inv.setItem(20, AdminMenuHolder.icon(Material.CHEST, "Browse schematics",
                "Opens the file list — click one to preview"));
        inv.setItem(22, AdminMenuHolder.icon(Material.WOODEN_AXE, "World edit panel",
                "Selection, fill, clipboard…"));
        inv.setItem(28, AdminMenuHolder.icon(Material.LIME_CONCRETE, NamedTextColor.GREEN, "Confirm paste",
                "Place the pending preview now"));
        inv.setItem(29, AdminMenuHolder.icon(Material.RED_CONCRETE, NamedTextColor.RED, "Cancel preview",
                "Abort pending paste outline"));
        inv.setItem(30, AdminMenuHolder.icon(Material.COMPASS, "Move preview here",
                "Shift paste box to your feet",
                "Walk first, then click"));
        inv.setItem(31, AdminMenuHolder.icon(Material.ORANGE_CONCRETE, "Undo last edit",
                "Undo last paste / fill"));
        inv.setItem(AdminMenuSlots.SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(AdminMenuSlots.SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    void openLeveledMobs(Player player) {
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.LEVELED_MOBS);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Leveled mobs", NamedTextColor.DARK_GREEN));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);

        boolean on = leveledEnabled();
        String strategy = leveledStrategy();
        String levels = leveledLevels();
        String blocks = leveledBlocks();
        boolean nametag = leveledNametag();

        inv.setItem(AdminMenuSlots.SLOT_INFO, AdminMenuHolder.icon(Material.ZOMBIE_HEAD, "YaPLeveledMobs",
                "Enabled: " + (on ? "yes" : "no"),
                "Strategy: " + strategy,
                "Levels: " + levels,
                "Blocks/level: " + blocks,
                "Nametag: " + (nametag ? "on" : "off")));

        inv.setItem(19, AdminMenuHolder.icon(on ? Material.LIME_DYE : Material.GRAY_DYE,
                on ? "Enabled: ON" : "Enabled: OFF",
                "Click to toggle leveling"));
        inv.setItem(20, AdminMenuHolder.icon(Material.COMPASS, "Strategy: " + strategy,
                "Click to cycle distance ↔ random"));
        inv.setItem(21, AdminMenuHolder.icon(Material.IRON_SWORD, "Min level -5",
                "Current range: " + levels));
        inv.setItem(22, AdminMenuHolder.icon(Material.DIAMOND_SWORD, "Max level +5",
                "Current range: " + levels));
        inv.setItem(23, AdminMenuHolder.icon(Material.MAP, "Blocks/level",
                "Current: " + blocks,
                "Click: −10 · Shift: +10"));
        inv.setItem(24, AdminMenuHolder.icon(nametag ? Material.NAME_TAG : Material.PAPER,
                nametag ? "Nametags: ON" : "Nametags: OFF",
                "Show Lv.X above mobs"));
        inv.setItem(25, AdminMenuHolder.icon(Material.EXPERIENCE_BOTTLE, "Reload",
                "/yaplevel reload"));
        inv.setItem(31, AdminMenuHolder.icon(Material.SPYGLASS, "Info look-target",
                "Look at a mob · /yaplevel info"));

        inv.setItem(AdminMenuSlots.SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(AdminMenuSlots.SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    private static Object leveledConfig() {
        var pl = Bukkit.getPluginManager().getPlugin("YaPLeveledMobs");
        if (pl == null || !pl.isEnabled()) {
            return null;
        }
        try {
            return pl.getClass().getMethod("leveledConfig").invoke(pl);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static boolean leveledEnabled() {
        Object cfg = leveledConfig();
        if (cfg == null) {
            return false;
        }
        try {
            Object v = cfg.getClass().getMethod("enabled").invoke(cfg);
            return v instanceof Boolean b && b;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private static String leveledStrategy() {
        Object cfg = leveledConfig();
        if (cfg == null) {
            return "n/a";
        }
        try {
            Object v = cfg.getClass().getMethod("strategy").invoke(cfg);
            return v == null ? "n/a" : v.toString();
        } catch (ReflectiveOperationException e) {
            return "n/a";
        }
    }

    private static String leveledLevels() {
        Object cfg = leveledConfig();
        if (cfg == null) {
            return "n/a";
        }
        try {
            int min = (Integer) cfg.getClass().getMethod("minLevel").invoke(cfg);
            int max = (Integer) cfg.getClass().getMethod("maxLevel").invoke(cfg);
            return min + "-" + max;
        } catch (ReflectiveOperationException e) {
            return "n/a";
        }
    }

    private static String leveledBlocks() {
        Object cfg = leveledConfig();
        if (cfg == null) {
            return "n/a";
        }
        try {
            double b = (Double) cfg.getClass().getMethod("blocksPerLevel").invoke(cfg);
            return String.format(Locale.ROOT, "%.0f", b);
        } catch (ReflectiveOperationException e) {
            return "n/a";
        }
    }

    private static boolean leveledNametag() {
        Object cfg = leveledConfig();
        if (cfg == null) {
            return false;
        }
        try {
            Object v = cfg.getClass().getMethod("nametagEnabled").invoke(cfg);
            return v instanceof Boolean b && b;
        } catch (ReflectiveOperationException e) {
            return false;
        }
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
        if (plugin.actions().pluginEnabled("YaPLeveledMobs")) {
            inv.setItem(24, AdminMenuHolder.icon(Material.ZOMBIE_HEAD, "Leveled mobs",
                    "Enable · strategy · levels"));
        }
        inv.setItem(AdminMenuSlots.SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(AdminMenuSlots.SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }
}
