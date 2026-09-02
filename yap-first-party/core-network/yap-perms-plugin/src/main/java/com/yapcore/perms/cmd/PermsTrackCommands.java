package com.yapcore.perms.cmd;

import com.yapcore.perms.PermsPlugin;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class PermsTrackCommands {
    private final PermsPlugin plugin;

    PermsTrackCommands(PermsPlugin plugin) {
        this.plugin = plugin;
    }

    boolean trackCmd(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapperm.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length == 0 || "list".equalsIgnoreCase(args[0])) {
            sender.sendMessage("§6Tracks: §f" + String.join(", ", plugin.resolver().tracks().keySet()));
            return true;
        }
        if (args.length >= 2 && "info".equalsIgnoreCase(args[0])) {
            var groups = plugin.resolver().tracks().get(args[1].toLowerCase(Locale.ROOT));
            if (groups == null) {
                sender.sendMessage("§cUnknown track.");
                return true;
            }
            sender.sendMessage("§6Track §f" + args[1] + "§6: §f" + String.join(" → ", groups));
            return true;
        }
        if (args.length >= 2 && "create".equalsIgnoreCase(args[0])) {
            String track = args[1].toLowerCase(Locale.ROOT);
            List<String> groups = new ArrayList<>();
            for (int i = 2; i < args.length; i++) {
                groups.add(args[i].toLowerCase(Locale.ROOT));
            }
            YapSched.async(plugin, () -> {
                try {
                    plugin.repository().replaceTrack(track, groups);
                    YapSched.global(plugin, plugin::reloadAll);
                    YapSched.global(plugin, () -> sender.sendMessage("§aCreated track §f" + track
                            + (groups.isEmpty() ? "" : " §7→ §f" + String.join(" → ", groups))));
                } catch (Exception e) {
                    YapSched.global(plugin, () -> sender.sendMessage("§cFailed: " + e.getMessage()));
                }
            });
            return true;
        }
        if (args.length >= 3 && "append".equalsIgnoreCase(args[0])) {
            String track = args[1].toLowerCase(Locale.ROOT);
            String group = args[2].toLowerCase(Locale.ROOT);
            YapSched.async(plugin, () -> {
                try {
                    List<String> groups = new ArrayList<>(
                            plugin.resolver().tracks().getOrDefault(track, List.of()));
                    if (!groups.contains(group)) {
                        groups.add(group);
                    }
                    plugin.repository().replaceTrack(track, groups);
                    YapSched.global(plugin, plugin::reloadAll);
                    YapSched.global(plugin, () -> sender.sendMessage("§aAppended §f" + group + " §ato §f" + track));
                } catch (Exception e) {
                    YapSched.global(plugin, () -> sender.sendMessage("§cFailed: " + e.getMessage()));
                }
            });
            return true;
        }
        if (args.length >= 3 && "remove".equalsIgnoreCase(args[0])) {
            String track = args[1].toLowerCase(Locale.ROOT);
            String group = args[2].toLowerCase(Locale.ROOT);
            YapSched.async(plugin, () -> {
                try {
                    List<String> groups = new ArrayList<>(
                            plugin.resolver().tracks().getOrDefault(track, List.of()));
                    groups.remove(group);
                    plugin.repository().replaceTrack(track, groups);
                    YapSched.global(plugin, plugin::reloadAll);
                    YapSched.global(plugin, () -> sender.sendMessage("§aRemoved §f" + group + " §afrom §f" + track));
                } catch (Exception e) {
                    YapSched.global(plugin, () -> sender.sendMessage("§cFailed: " + e.getMessage()));
                }
            });
            return true;
        }
        if (args.length >= 2 && "delete".equalsIgnoreCase(args[0])) {
            String track = args[1].toLowerCase(Locale.ROOT);
            YapSched.async(plugin, () -> {
                try {
                    plugin.repository().deleteTrack(track);
                    YapSched.global(plugin, plugin::reloadAll);
                    YapSched.global(plugin, () -> sender.sendMessage("§aDeleted track §f" + track));
                } catch (Exception e) {
                    YapSched.global(plugin, () -> sender.sendMessage("§cFailed: " + e.getMessage()));
                }
            });
            return true;
        }
        sender.sendMessage("§e/yapperm track list|info|create|append|remove|delete …");
        return true;
    }

    boolean promote(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapperm.promote")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§e/promote <player> [track]");
            return true;
        }
        return trackStep(sender, args[0], args.length >= 2 ? args[1] : plugin.config().defaultTrack(), 1, "promoted");
    }

    boolean demote(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapperm.demote")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§e/demote <player> [track]");
            return true;
        }
        return trackStep(sender, args[0], args.length >= 2 ? args[1] : plugin.config().defaultTrack(), -1, "demoted");
    }

    boolean trackStep(CommandSender sender, String playerName, String track, int delta, String verb) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        YapSched.async(plugin, () -> {
            try {
                Optional<String> next = plugin.repository().trackStep(
                        target.getUniqueId(), playerName, track, delta);
                YapSched.global(plugin, () -> {
                    if (next.isEmpty()) {
                        sender.sendMessage("§cCould not " + verb + " §f" + playerName + " §con track §f" + track);
                        return;
                    }
                    plugin.refreshOnline(target.getUniqueId());
                    sender.sendMessage("§a" + playerName + " " + verb + " to §f" + next.get());
                });
            } catch (Exception e) {
                YapSched.global(plugin, () ->
                        sender.sendMessage("§cFailed: " + e.getMessage()));
            }
        });
        return true;
    }
}
