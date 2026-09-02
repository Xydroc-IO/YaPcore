package com.yapcore.perms.cmd;

import com.yapcore.perms.PermsPlugin;
import com.yapcore.perms.io.PermsDump;
import com.yapcore.sched.YapSched;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class PermsIoCommands {
    private final PermsPlugin plugin;

    PermsIoCommands(PermsPlugin plugin) {
        this.plugin = plugin;
    }

    boolean dumpCmd(CommandSender sender) {
        if (!sender.hasPermission("yapperm.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        File file = new File(plugin.getDataFolder(), "editor-snapshot.yml");
        try {
            PermsDump.exportEditorSnapshot(file, plugin.repository());
            sender.sendMessage("§aDumped group nodes to §f" + file.getName());
        } catch (Exception e) {
            sender.sendMessage("§cDump failed: " + e.getMessage());
        }
        return true;
    }

    boolean editorApplyCmd(CommandSender sender) {
        if (!sender.hasPermission("yapperm.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        File pending = new File(plugin.getDataFolder(), "editor-apply.yml");
        try {
            if (pending.isFile()) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(pending);
                String group = yaml.getString("group", "").trim().toLowerCase(Locale.ROOT);
                if (group.isEmpty()) {
                    sender.sendMessage("§ceditor-apply.yml missing group.");
                    return true;
                }
                plugin.repository().applyEditorBatch(group,
                        yaml.getStringList("allow"),
                        yaml.getStringList("deny"),
                        yaml.getStringList("unset"));
                PermsDump.exportEditorSnapshot(new File(plugin.getDataFolder(), "editor-snapshot.yml"),
                        plugin.repository());
                plugin.reloadAll();
                sender.sendMessage("§aApplied editor batch for §f" + group);
                return true;
            }
            plugin.config().reload();
            int groups = 0;
            int nodes = 0;
            for (var entry : plugin.config().editorNodes().entrySet()) {
                List<String> allow = new ArrayList<>();
                List<String> deny = new ArrayList<>();
                for (var node : entry.getValue().entrySet()) {
                    if (Boolean.TRUE.equals(node.getValue())) {
                        allow.add(node.getKey());
                    } else {
                        deny.add(node.getKey());
                    }
                    nodes++;
                }
                plugin.repository().applyEditorBatch(entry.getKey(), allow, deny, List.of());
                groups++;
            }
            PermsDump.exportEditorSnapshot(new File(plugin.getDataFolder(), "editor-snapshot.yml"),
                    plugin.repository());
            plugin.reloadAll();
            sender.sendMessage("§aApplied editor-nodes for §f" + groups + " §agroups (§f" + nodes + " §anodes).");
        } catch (Exception e) {
            sender.sendMessage("§cEditor apply failed: " + e.getMessage());
        }
        return true;
    }

    boolean exportCmd(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapperm.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        String name = args.length >= 1 ? args[0] : "export.yml";
        java.io.File file = new java.io.File(plugin.getDataFolder(), name);
        YapSched.async(plugin, () -> {
            try {
                PermsDump.exportTo(file, plugin.repository());
                YapSched.global(plugin, () ->
                        sender.sendMessage("§aExported permissions to §f" + file.getName()));
            } catch (Exception e) {
                YapSched.global(plugin, () -> sender.sendMessage("§cExport failed: " + e.getMessage()));
            }
        });
        return true;
    }

    boolean importCmd(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapperm.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§e/yapperm import <file.yml>");
            return true;
        }
        java.io.File file = new java.io.File(plugin.getDataFolder(), args[0]);
        if (!file.isFile()) {
            sender.sendMessage("§cMissing file: §f" + file.getName());
            return true;
        }
        YapSched.async(plugin, () -> {
            try {
                int n = PermsDump.importFrom(file, plugin.repository());
                YapSched.global(plugin, plugin::reloadAll);
                YapSched.global(plugin, () ->
                        sender.sendMessage("§aImported §f" + n + " §arows from §f" + file.getName()));
            } catch (Exception e) {
                YapSched.global(plugin, () -> sender.sendMessage("§cImport failed: " + e.getMessage()));
            }
        });
        return true;
    }
}
