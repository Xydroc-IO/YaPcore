package com.yapcore.plugincompat.paper;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Tier A runtime face for plugin back-compat (1.20–1.21 → Paper 26.2).
 * Heavy lifting is YaPcore's pre-load ASM rewrite; this plugin documents status.
 */
public final class YapPluginCompatPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("YaP Plugin Compat active — 1.20–1.21 field/package rewrite "
                + "(pre-Paper) + runtime helpers. See /yapcompat");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("yapcompat")) {
            return false;
        }
        sender.sendMessage("§aYaP Plugin Compat (Tier A + light B)");
        sender.sendMessage("§7Target: Paper 1.20–1.21 jars on Paper 26.2");
        sender.sendMessage("§7Rewrites: Enchantment/Potion/Particle legacy fields, "
                + "CraftBukkit v1_20_R*/v1_21_R* → unversioned");
        sender.sendMessage("§7Backups: plugins/.yap-plugin-compat-backup/");
        sender.sendMessage("§7Config: plugin-compat-enabled / rewrite / backup in server.properties");
        sender.sendMessage("§7Server: " + Bukkit.getVersion());
        return true;
    }
}
