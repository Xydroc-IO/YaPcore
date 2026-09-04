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

final class PermsGroupCommands {
    private final PermsPlugin plugin;

    PermsGroupCommands(PermsPlugin plugin) {
        this.plugin = plugin;
    }

    boolean groupCmd(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapperm.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§e/yapperm group list|info <group>|permission set <group> <node> true|false");
            return true;
        }
        if ("list".equalsIgnoreCase(args[0])) {
            sender.sendMessage("§6Groups: §f" + String.join(", ", plugin.resolver().groups().keySet()));
            return true;
        }
        if (args.length >= 2 && "info".equalsIgnoreCase(args[0])) {
            var row = plugin.resolver().groups().get(args[1].toLowerCase(Locale.ROOT));
            if (row == null) {
                sender.sendMessage("§cUnknown group.");
                return true;
            }
            sender.sendMessage("§6" + row.name() + " §7weight §f" + row.weight()
                    + " §7parents §f" + String.join(", ", row.parents()));
            sender.sendMessage("§7Prefix: §r" + PermsCmdSupport.color(row.prefix()) + "Name" + PermsCmdSupport.color(row.suffix()));
            sender.sendMessage("§7Name color: §r" + PermsCmdSupport.color(ChatColors.orDefault(row.nameColor(), "&f"))
                    + "Steve §7 Chat color: §r" + PermsCmdSupport.color(ChatColors.orDefault(row.chatColor(), "&f")) + "hello");
            var now = java.time.Instant.now();
            int shown = 0;
            for (StoredNode node : row.nodes()) {
                if (node.expired(now)) {
                    continue;
                }
                String flag = node.value() ? "§atrue" : "§cfalse";
                String extra = "";
                if (!node.world().isBlank()) {
                    extra += " §8world=" + node.world();
                }
                if (!node.server().isBlank()) {
                    extra += " §8server=" + node.server();
                }
                if (node.expiresAt() != null) {
                    extra += " §8exp=" + node.expiresAt();
                }
                sender.sendMessage("§8 • §f" + node.node() + " §7= " + flag + extra);
                if (++shown >= 80) {
                    sender.sendMessage("§7… " + (row.nodes().size() - shown) + " more");
                    break;
                }
            }
            if (shown == 0) {
                sender.sendMessage("§7No explicit permission nodes.");
            }
            return true;
        }
        if (args.length >= 5 && "permission".equalsIgnoreCase(args[0]) && "set".equalsIgnoreCase(args[1])) {
            String group = args[2].toLowerCase(Locale.ROOT);
            String node = args[3];
            boolean value = Boolean.parseBoolean(args[4]);
            NodeArgParser.Context ctx = NodeArgParser.parse(args, 5);
            YapSched.async(plugin, () -> {
                try {
                    plugin.repository().setGroupNode(group, node, value, ctx.world(),
                            ctx.server().isBlank() ? plugin.config().serverContext() : ctx.server(),
                            ctx.expires());
                    YapSched.global(plugin, plugin::reloadAll);
                    YapSched.global(plugin, () ->
                            sender.sendMessage("§aSet group §f" + group + " §anode §f" + node + " §a= §f" + value
                                    + NodeArgParser.describe(ctx)));
                } catch (Exception e) {
                    YapSched.global(plugin, () ->
                            sender.sendMessage("§cFailed: " + e.getMessage()));
                }
            });
            return true;
        }
        if (args.length >= 4 && "permission".equalsIgnoreCase(args[0]) && "unset".equalsIgnoreCase(args[1])) {
            String group = args[2].toLowerCase(Locale.ROOT);
            String node = args[3];
            NodeArgParser.Context ctx = NodeArgParser.parse(args, 4);
            YapSched.async(plugin, () -> {
                try {
                    plugin.repository().unsetGroupNode(group, node, ctx.world(),
                            ctx.server().isBlank() ? plugin.config().serverContext() : ctx.server());
                    YapSched.global(plugin, plugin::reloadAll);
                    YapSched.global(plugin, () ->
                            sender.sendMessage("§aUnset group §f" + group + " §anode §f" + node));
                } catch (Exception e) {
                    YapSched.global(plugin, () -> sender.sendMessage("§cFailed: " + e.getMessage()));
                }
            });
            return true;
        }
        if (args.length >= 4 && "parent".equalsIgnoreCase(args[0]) && "add".equalsIgnoreCase(args[1])) {
            String group = args[2].toLowerCase(Locale.ROOT);
            String parent = args[3].toLowerCase(Locale.ROOT);
            YapSched.async(plugin, () -> {
                try {
                    plugin.repository().addGroupParent(group, parent);
                    YapSched.global(plugin, plugin::reloadAll);
                    YapSched.global(plugin, () ->
                            sender.sendMessage("§aAdded parent §f" + parent + " §ato §f" + group));
                } catch (Exception e) {
                    YapSched.global(plugin, () -> sender.sendMessage("§cFailed: " + e.getMessage()));
                }
            });
            return true;
        }
        if (args.length >= 4 && "parent".equalsIgnoreCase(args[0]) && "remove".equalsIgnoreCase(args[1])) {
            String group = args[2].toLowerCase(Locale.ROOT);
            String parent = args[3].toLowerCase(Locale.ROOT);
            YapSched.async(plugin, () -> {
                try {
                    plugin.repository().removeGroupParent(group, parent);
                    YapSched.global(plugin, plugin::reloadAll);
                    YapSched.global(plugin, () ->
                            sender.sendMessage("§aRemoved parent §f" + parent + " §afrom §f" + group));
                } catch (Exception e) {
                    YapSched.global(plugin, () -> sender.sendMessage("§cFailed: " + e.getMessage()));
                }
            });
            return true;
        }
        if (args.length >= 4 && "parent".equalsIgnoreCase(args[0]) && "set".equalsIgnoreCase(args[1])) {
            String group = args[2].toLowerCase(Locale.ROOT);
            List<String> parents = new ArrayList<>();
            for (int i = 3; i < args.length; i++) {
                parents.add(args[i].toLowerCase(Locale.ROOT));
            }
            YapSched.async(plugin, () -> {
                try {
                    plugin.repository().replaceParents(group, parents);
                    YapSched.global(plugin, plugin::reloadAll);
                    YapSched.global(plugin, () ->
                            sender.sendMessage("§aSet parents of §f" + group + " §ato §f" + String.join(", ", parents)));
                } catch (Exception e) {
                    YapSched.global(plugin, () -> sender.sendMessage("§cFailed: " + e.getMessage()));
                }
            });
            return true;
        }
        if (args.length >= 2 && "create".equalsIgnoreCase(args[0])) {
            String group = args[1].toLowerCase(Locale.ROOT);
            int weight = args.length >= 3 ? PermsCmdSupport.parseInt(args[2], 0) : 0;
            YapSched.async(plugin, () -> {
                try {
                    plugin.repository().upsertGroup(group, weight, "", "");
                    YapSched.global(plugin, plugin::reloadAll);
                    YapSched.global(plugin, () -> sender.sendMessage("§aCreated group §f" + group));
                } catch (Exception e) {
                    YapSched.global(plugin, () -> sender.sendMessage("§cFailed: " + e.getMessage()));
                }
            });
            return true;
        }
        if (args.length >= 2 && "delete".equalsIgnoreCase(args[0])) {
            String group = args[1].toLowerCase(Locale.ROOT);
            YapSched.async(plugin, () -> {
                try {
                    plugin.repository().deleteGroup(group);
                    YapSched.global(plugin, plugin::reloadAll);
                    YapSched.global(plugin, () -> sender.sendMessage("§aDeleted group §f" + group));
                } catch (Exception e) {
                    YapSched.global(plugin, () -> sender.sendMessage("§cFailed: " + e.getMessage()));
                }
            });
            return true;
        }
        if (args.length >= 3 && "setprefix".equalsIgnoreCase(args[0])) {
            var row = plugin.resolver().groups().get(args[1].toLowerCase(Locale.ROOT));
            if (row == null) {
                sender.sendMessage("§cUnknown group.");
                return true;
            }
            String prefix = PermsCmdSupport.joinFrom(args, 2);
            YapSched.async(plugin, () -> {
                try {
                    plugin.repository().upsertGroup(row.name(), row.weight(), prefix, row.suffix(),
                            row.nameColor(), row.chatColor());
                    YapSched.global(plugin, plugin::reloadAll);
                    YapSched.global(plugin, () -> sender.sendMessage("§aUpdated prefix for §f" + row.name()));
                } catch (Exception e) {
                    YapSched.global(plugin, () -> sender.sendMessage("§cFailed: " + e.getMessage()));
                }
            });
            return true;
        }
        if (args.length >= 3 && "setsuffix".equalsIgnoreCase(args[0])) {
            var row = plugin.resolver().groups().get(args[1].toLowerCase(Locale.ROOT));
            if (row == null) {
                sender.sendMessage("§cUnknown group.");
                return true;
            }
            String suffix = PermsCmdSupport.joinFrom(args, 2);
            YapSched.async(plugin, () -> {
                try {
                    plugin.repository().upsertGroup(row.name(), row.weight(), row.prefix(), suffix,
                            row.nameColor(), row.chatColor());
                    YapSched.global(plugin, plugin::reloadAll);
                    YapSched.global(plugin, () -> sender.sendMessage("§aUpdated suffix for §f" + row.name()));
                } catch (Exception e) {
                    YapSched.global(plugin, () -> sender.sendMessage("§cFailed: " + e.getMessage()));
                }
            });
            return true;
        }
        if (args.length >= 3 && ("setnamecolor".equalsIgnoreCase(args[0])
                || "setchatcolor".equalsIgnoreCase(args[0]))) {
            var row = plugin.resolver().groups().get(args[1].toLowerCase(Locale.ROOT));
            if (row == null) {
                sender.sendMessage("§cUnknown group.");
                return true;
            }
            String color = ChatColors.normalize(PermsCmdSupport.joinFrom(args, 2));
            boolean name = "setnamecolor".equalsIgnoreCase(args[0]);
            String nameColor = name ? color : row.nameColor();
            String chatColor = name ? row.chatColor() : color;
            YapSched.async(plugin, () -> {
                try {
                    plugin.repository().upsertGroup(row.name(), row.weight(), row.prefix(), row.suffix(),
                            nameColor, chatColor);
                    YapSched.global(plugin, plugin::reloadAll);
                    YapSched.global(plugin, () -> sender.sendMessage("§aUpdated "
                            + (name ? "name" : "chat") + " color for §f" + row.name()
                            + " §7to §r" + PermsCmdSupport.color(ChatColors.orDefault(color, "&f")) + "this"));
                } catch (Exception e) {
                    YapSched.global(plugin, () -> sender.sendMessage("§cFailed: " + e.getMessage()));
                }
            });
            return true;
        }
        sender.sendMessage("§e/yapperm group create|delete|list|info|setprefix|setsuffix|setnamecolor|setchatcolor");
        sender.sendMessage("§e/yapperm group parent add|remove|set <group> <parent…>");
        sender.sendMessage("§e/yapperm group permission set|unset <group> <node> [true|false] [1d] [world=x] [server=y]");
        return true;
    }
}
