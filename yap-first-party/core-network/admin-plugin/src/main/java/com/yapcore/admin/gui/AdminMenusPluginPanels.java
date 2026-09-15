package com.yapcore.admin.gui;

import com.yapcore.admin.AdminPlugin;
import com.yapcore.admin.session.AdminSession;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Locale;

/** Leveled-mobs and QoL plugin panels for admin menus. */
final class AdminMenusPluginPanels {

    private final AdminPlugin plugin;

    AdminMenusPluginPanels(AdminPlugin plugin) {
        this.plugin = plugin;
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

    void openQolTools(Player player) {
        if (!plugin.actions().pluginEnabled("YaP-QoL")) {
            player.sendMessage("§cYaP-QoL is not loaded.");
            return;
        }
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.QOL);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("QoL tools", NamedTextColor.GREEN));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);

        AdminSession session = plugin.session(player.getUniqueId());
        String target = session.hasTarget() ? session.targetName() : player.getName() + " (self)";
        boolean on = qolEnabled();
        boolean timber = qolTimberEnabled();
        boolean excavator = qolExcavatorEnabled();
        String sizes = qolSizesLabel();

        inv.setItem(AdminMenuSlots.SLOT_INFO, AdminMenuHolder.icon(Material.GOLDEN_AXE, "YaP-QoL",
                "Master: " + (on ? "on" : "off"),
                "Timber: " + (timber ? "on" : "off") + " · Excavator: " + (excavator ? "on" : "off"),
                "Excavator sizes: " + sizes,
                "Give target: " + target));

        inv.setItem(19, AdminMenuHolder.icon(on ? Material.LIME_DYE : Material.GRAY_DYE,
                on ? "Plugin: ON" : "Plugin: OFF",
                "Click to toggle YaP-QoL master switch"));
        inv.setItem(20, AdminMenuHolder.icon(timber ? Material.LIME_DYE : Material.GRAY_DYE,
                timber ? "Timber axe: ON" : "Timber axe: OFF",
                "Whole-tree chop when holding the special axe"));
        inv.setItem(21, AdminMenuHolder.icon(excavator ? Material.LIME_DYE : Material.GRAY_DYE,
                excavator ? "Excavator: ON" : "Excavator: OFF",
                "Flat area mining with the special pickaxe"));

        inv.setItem(28, AdminMenuHolder.icon(Material.GOLDEN_AXE, NamedTextColor.GREEN, "Give Timber Axe",
                "To: " + target,
                "Select a player under Players first (optional)"));
        inv.setItem(29, AdminMenuHolder.icon(Material.GOLDEN_PICKAXE, NamedTextColor.AQUA, "Give Excavator 3×3",
                "To: " + target));
        inv.setItem(30, AdminMenuHolder.icon(Material.GOLDEN_PICKAXE, NamedTextColor.AQUA, "Give Excavator 6×6",
                "To: " + target));
        inv.setItem(31, AdminMenuHolder.icon(Material.GOLDEN_PICKAXE, NamedTextColor.AQUA, "Give Excavator 9×9",
                "To: " + target));
        inv.setItem(33, AdminMenuHolder.icon(Material.PLAYER_HEAD, "Pick give target",
                "Opens online players",
                "Then return here to give tools"));
        inv.setItem(34, AdminMenuHolder.icon(Material.EXPERIENCE_BOTTLE, "Reload",
                "/yapqol reload"));

        inv.setItem(AdminMenuSlots.SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(AdminMenuSlots.SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    private static Object qolPlugin() {
        var pl = Bukkit.getPluginManager().getPlugin("YaP-QoL");
        if (pl == null || !pl.isEnabled()) {
            return null;
        }
        return pl;
    }

    private static Object qolConfig() {
        Object pl = qolPlugin();
        if (pl == null) {
            return null;
        }
        try {
            return pl.getClass().getMethod("qolConfig").invoke(pl);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static boolean qolEnabled() {
        Object cfg = qolConfig();
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

    private static boolean qolTimberEnabled() {
        Object cfg = qolConfig();
        if (cfg == null) {
            return false;
        }
        try {
            Object v = cfg.getClass().getMethod("timberEnabled").invoke(cfg);
            return v instanceof Boolean b && b;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private static boolean qolExcavatorEnabled() {
        Object cfg = qolConfig();
        if (cfg == null) {
            return false;
        }
        try {
            Object v = cfg.getClass().getMethod("excavatorEnabled").invoke(cfg);
            return v instanceof Boolean b && b;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private static String qolSizesLabel() {
        Object cfg = qolConfig();
        if (cfg == null) {
            return "3,6,9";
        }
        try {
            Object v = cfg.getClass().getMethod("excavatorSizes").invoke(cfg);
            return v == null ? "3,6,9" : v.toString().replace("[", "").replace("]", "");
        } catch (ReflectiveOperationException e) {
            return "3,6,9";
        }
    }
}
