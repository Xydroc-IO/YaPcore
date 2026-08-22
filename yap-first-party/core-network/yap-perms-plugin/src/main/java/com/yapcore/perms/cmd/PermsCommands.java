package com.yapcore.perms.cmd;

import com.yapcore.perms.EffectiveUser;
import com.yapcore.perms.PermsPlugin;
import com.yapcore.perms.db.PermsRepository;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public final class PermsCommands implements CommandExecutor, TabCompleter {

    private final PermsPlugin plugin;

    public PermsCommands(PermsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if ("promote".equals(name)) {
            return promote(sender, args);
        }
        if ("demote".equals(name)) {
            return demote(sender, args);
        }
        return yapperm(sender, args);
    }

    private boolean yapperm(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§e/yapperm user|group|track|reload|applypack");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "reload" -> {
                if (!sender.hasPermission("yapperm.admin")) {
                    sender.sendMessage("§cNo permission.");
                    yield true;
                }
                plugin.reloadAll();
                sender.sendMessage("§aYaPPerms reloaded.");
                yield true;
            }
            case "applypack" -> {
                if (!sender.hasPermission("yapperm.admin")) {
                    sender.sendMessage("§cNo permission.");
                    yield true;
                }
                YapSched.async(plugin, () -> {
                    try {
                        plugin.repository().applyStarterPackFromConfig();
                        YapSched.global(plugin, () -> {
                            plugin.reloadAll();
                            sender.sendMessage("§aStarter rank pack applied.");
                        });
                    } catch (Exception e) {
                        YapSched.global(plugin, () ->
                                sender.sendMessage("§cApply pack failed: " + e.getMessage()));
                    }
                });
                yield true;
            }
            case "user" -> userCmd(sender, Arrays.copyOfRange(args, 1, args.length));
            case "group" -> groupCmd(sender, Arrays.copyOfRange(args, 1, args.length));
            case "track" -> trackCmd(sender, Arrays.copyOfRange(args, 1, args.length));
            default -> {
                sender.sendMessage("§e/yapperm user|group|track|reload|applypack");
                yield true;
            }
        };
    }

    private boolean userCmd(CommandSender sender, String[] args) {
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
            sender.sendMessage("§6" + eff.name() + " §7— group §f" + eff.primaryGroup()
                    + " §7weight §f" + eff.weight());
            sender.sendMessage("§7Prefix: §r" + color(eff.prefix()) + "Name" + color(eff.suffix()));
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
            String combined = joinFrom(args, 3);
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
            YapSched.async(plugin, () -> {
                try {
                    plugin.repository().setUserNode(uuid, playerName, node, value);
                    YapSched.global(plugin, () -> {
                        plugin.refreshOnline(uuid);
                        sender.sendMessage("§aSet §f" + node + " §a= §f" + value + " §afor §f" + playerName);
                    });
                } catch (Exception e) {
                    YapSched.global(plugin, () -> sender.sendMessage("§cFailed: " + e.getMessage()));
                }
            });
            return true;
        }
        sender.sendMessage("§e/yapperm user <player> parent set|add|remove <group>");
        sender.sendMessage("§e/yapperm user <player> meta set <prefix> [suffix] | meta clear");
        sender.sendMessage("§e/yapperm user <player> permission set <node> true|false");
        return true;
    }

    private boolean groupCmd(CommandSender sender, String[] args) {
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
            sender.sendMessage("§7Prefix: §r" + color(row.prefix()) + "Name" + color(row.suffix()));
            return true;
        }
        if (args.length >= 5 && "permission".equalsIgnoreCase(args[0]) && "set".equalsIgnoreCase(args[1])) {
            String group = args[2].toLowerCase(Locale.ROOT);
            String node = args[3];
            boolean value = Boolean.parseBoolean(args[4]);
            YapSched.async(plugin, () -> {
                try {
                    plugin.repository().setGroupNode(group, node, value);
                    YapSched.global(plugin, plugin::reloadAll);
                    YapSched.global(plugin, () ->
                            sender.sendMessage("§aSet group §f" + group + " §anode §f" + node + " §a= §f" + value));
                } catch (Exception e) {
                    YapSched.global(plugin, () ->
                            sender.sendMessage("§cFailed: " + e.getMessage()));
                }
            });
            return true;
        }
        if (args.length >= 2 && "create".equalsIgnoreCase(args[0])) {
            String group = args[1].toLowerCase(Locale.ROOT);
            int weight = args.length >= 3 ? parseInt(args[2], 0) : 0;
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
            String prefix = joinFrom(args, 2);
            YapSched.async(plugin, () -> {
                try {
                    plugin.repository().upsertGroup(row.name(), row.weight(), prefix, row.suffix());
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
            String suffix = joinFrom(args, 2);
            YapSched.async(plugin, () -> {
                try {
                    plugin.repository().upsertGroup(row.name(), row.weight(), row.prefix(), suffix);
                    YapSched.global(plugin, plugin::reloadAll);
                    YapSched.global(plugin, () -> sender.sendMessage("§aUpdated suffix for §f" + row.name()));
                } catch (Exception e) {
                    YapSched.global(plugin, () -> sender.sendMessage("§cFailed: " + e.getMessage()));
                }
            });
            return true;
        }
        sender.sendMessage("§e/yapperm group create|delete|list|info|setprefix|setsuffix|permission set …");
        return true;
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String joinFrom(String[] args, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (i > start) {
                sb.append(' ');
            }
            sb.append(args[i]);
        }
        return sb.toString();
    }

    private boolean trackCmd(CommandSender sender, String[] args) {
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
        sender.sendMessage("§e/yapperm track list|info <track>");
        return true;
    }

    private boolean promote(CommandSender sender, String[] args) {
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

    private boolean demote(CommandSender sender, String[] args) {
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

    private boolean trackStep(CommandSender sender, String playerName, String track, int delta, String verb) {
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

    private static String color(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace('&', '§');
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("yapperm.admin")) {
            return List.of();
        }
        if ("promote".equalsIgnoreCase(command.getName()) || "demote".equalsIgnoreCase(command.getName())) {
            if (args.length == 1) {
                return partial(args[0], onlineNames());
            }
            if (args.length == 2) {
                return partial(args[1], plugin.resolver().tracks().keySet());
            }
            return List.of();
        }
        if (args.length == 1) {
            return partial(args[0], List.of("user", "group", "track", "reload", "applypack"));
        }
        if ("group".equalsIgnoreCase(args[0]) && args.length == 2) {
            return partial(args[1], List.of("list", "info", "create", "delete", "setprefix", "setsuffix", "permission"));
        }
        if ("track".equalsIgnoreCase(args[0]) && args.length == 2) {
            return partial(args[1], List.of("list", "info"));
        }
        return List.of();
    }

    private static List<String> partial(String token, Iterable<String> options) {
        String lower = token.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }

    private static List<String> onlineNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
    }
}
