package com.yapcore.perms.cmd;

import com.yapcore.perms.ChatColors;
import com.yapcore.perms.EffectiveUser;
import com.yapcore.perms.PermsPlugin;
import com.yapcore.perms.engine.DurationParser;
import com.yapcore.perms.engine.StoredNode;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class PermsUserCommands {
    private final PermsPlugin plugin;

    PermsUserCommands(PermsPlugin plugin) {
        this.plugin = plugin;
    }

    boolean userCmd(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapperm.admin") && !sender.hasPermission("yapperm.user")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§e/yapperm user <player> [info|parent set <group>|permission set <node> true|false]");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        UUID uuid = target.getUniqueId();
        String playerName = target.getName() != null ? target.getName() : args[0];
        if (args.length == 1 || "info".equalsIgnoreCase(args[1])) {
            EffectiveUser eff = plugin.resolve(uuid, playerName);
            sender.sendMessage("§6" + eff.name() + " §7— primary §f" + eff.primaryGroup()
                    + " §7display §f" + eff.displayGroup()
                    + " §7weight §f" + eff.weight());
            sender.sendMessage("§7Groups: §f" + String.join(", ", eff.groups()));
            sender.sendMessage("§7Prefix: §r" + PermsCmdSupport.color(eff.prefix()) + "Name" + PermsCmdSupport.color(eff.suffix()));
            YapSched.async(plugin, () -> {
                try {
                    var user = plugin.repository().loadUser(uuid, playerName, plugin.config().defaultGroup());
                    List<String> temps = new ArrayList<>();
                    for (StoredNode node : user.nodes()) {
                        if (node.temporary() && !node.expired(java.time.Instant.now())) {
                            temps.add(node.node() + "=" + node.value()
                                    + (node.world().isBlank() ? "" : " world=" + node.world())
                                    + " " + DurationParser.format(
                                    java.time.Duration.between(java.time.Instant.now(), node.expiresAt())));
                        }
                    }
                    if (!temps.isEmpty()) {
                        YapSched.global(plugin, () -> sender.sendMessage("§7Temp: §f" + String.join("§7, §f", temps)));
                    }
                } catch (Exception ignored) {
                }
            });
            return true;
        }
        if (!sender.hasPermission("yapperm.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length >= 4 && "parent".equalsIgnoreCase(args[1]) && "set".equalsIgnoreCase(args[2])) {
            String group = args[3].toLowerCase(Locale.ROOT);
            YapSched.async(plugin, () -> {
                try {
                    plugin.repository().setPrimaryGroup(uuid, playerName, group);
                    YapSched.global(plugin, () -> {
                        plugin.refreshOnline(uuid);
                        sender.sendMessage("§aSet primary group of §f" + playerName + " §ato §f" + group);
                    });
                } catch (Exception e) {
                    YapSched.global(plugin, () -> sender.sendMessage("§cFailed: " + e.getMessage()));
                }
            });
            return true;
        }
        if (args.length >= 4 && "parent".equalsIgnoreCase(args[1]) && "add".equalsIgnoreCase(args[2])) {
            String group = args[3].toLowerCase(Locale.ROOT);
            YapSched.async(plugin, () -> {
                try {
                    plugin.repository().addUserParent(uuid, playerName, group);
                    YapSched.global(plugin, () -> {
                        plugin.refreshOnline(uuid);
                        sender.sendMessage("§aAdded group §f" + group + " §ato §f" + playerName);
                    });
                } catch (Exception e) {
                    YapSched.global(plugin, () -> sender.sendMessage("§cFailed: " + e.getMessage()));
                }
            });
            return true;
        }
        if (args.length >= 4 && "parent".equalsIgnoreCase(args[1]) && "remove".equalsIgnoreCase(args[2])) {
            String group = args[3].toLowerCase(Locale.ROOT);
            YapSched.async(plugin, () -> {
                try {
                    plugin.repository().removeUserParent(uuid, playerName, group);
                    YapSched.global(plugin, () -> {
                        plugin.refreshOnline(uuid);
                        sender.sendMessage("§aRemoved group §f" + group + " §afrom §f" + playerName);
                    });
                } catch (Exception e) {
                    YapSched.global(plugin, () -> sender.sendMessage("§cFailed: " + e.getMessage()));
                }
            });
            return true;
        }
        if (args.length >= 4 && "meta".equalsIgnoreCase(args[1]) && "set".equalsIgnoreCase(args[2])) {
            String combined = PermsCmdSupport.joinFrom(args, 3);
            String prefix;
            String suffix;
            int sep = combined.indexOf('\u001E');
            if (sep >= 0) {
                prefix = combined.substring(0, sep);
                suffix = combined.substring(sep + 1);
            } else {
                prefix = combined;
                suffix = "";
            }
            YapSched.async(plugin, () -> {
                try {
                    plugin.repository().setUserMeta(uuid, playerName, prefix, suffix);
                    YapSched.global(plugin, () -> {
                        plugin.refreshOnline(uuid);
                        sender.sendMessage("§aUpdated meta for §f" + playerName);
                    });
                } catch (Exception e) {
                    YapSched.global(plugin, () -> sender.sendMessage("§cFailed: " + e.getMessage()));
                }
            });
            return true;
        }
        if (args.length >= 3 && "meta".equalsIgnoreCase(args[1]) && "clear".equalsIgnoreCase(args[2])) {
            YapSched.async(plugin, () -> {
                try {
                    plugin.repository().clearUserMeta(uuid);
                    YapSched.global(plugin, () -> {
                        plugin.refreshOnline(uuid);
                        sender.sendMessage("§aCleared meta for §f" + playerName);
                    });
                } catch (Exception e) {
                    YapSched.global(plugin, () -> sender.sendMessage("§cFailed: " + e.getMessage()));
                }
            });
            return true;
        }
        if (args.length >= 5 && "permission".equalsIgnoreCase(args[1]) && "set".equalsIgnoreCase(args[2])) {
            String node = args[3];
            boolean value = Boolean.parseBoolean(args[4]);
            NodeArgParser.Context ctx = NodeArgParser.parse(args, 5);
            YapSched.async(plugin, () -> {
                try {
                    plugin.repository().setUserNode(uuid, playerName, node, value,
                            ctx.world(), ctx.server().isBlank() ? plugin.config().serverContext() : ctx.server(),
                            ctx.expires());
                    YapSched.global(plugin, () -> {
                        plugin.refreshOnline(uuid);
                        sender.sendMessage("§aSet §f" + node + " §a= §f" + value + " §afor §f" + playerName
                                + NodeArgParser.describe(ctx));
                    });
                } catch (Exception e) {
                    YapSched.global(plugin, () -> sender.sendMessage("§cFailed: " + e.getMessage()));
                }
            });
            return true;
        }
        if (args.length >= 4 && "permission".equalsIgnoreCase(args[1]) && "unset".equalsIgnoreCase(args[2])) {
            String node = args[3];
            NodeArgParser.Context ctx = NodeArgParser.parse(args, 4);
            YapSched.async(plugin, () -> {
                try {
                    plugin.repository().unsetUserNode(uuid, node, ctx.world(),
                            ctx.server().isBlank() ? plugin.config().serverContext() : ctx.server());
                    YapSched.global(plugin, () -> {
                        plugin.refreshOnline(uuid);
                        sender.sendMessage("§aUnset §f" + node + " §afor §f" + playerName
                                + NodeArgParser.describe(ctx));
                    });
                } catch (Exception e) {
                    YapSched.global(plugin, () -> sender.sendMessage("§cFailed: " + e.getMessage()));
                }
            });
            return true;
        }
        sender.sendMessage("§e/yapperm user <player> parent set|add|remove <group>");
        sender.sendMessage("§e/yapperm user <player> meta set <prefix> [suffix] | meta clear");
        sender.sendMessage("§e/yapperm user <player> permission set <node> true|false [1d] [world=x] [server=y]");
        sender.sendMessage("§e/yapperm user <player> permission unset <node> [world=x] [server=y]");
        return true;
    }
}
