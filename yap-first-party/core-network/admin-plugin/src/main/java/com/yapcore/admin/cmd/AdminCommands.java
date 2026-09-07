package com.yapcore.admin.cmd;

import com.yapcore.admin.AdminPlugin;
import com.yapcore.admin.action.AdminActions;
import com.yapcore.messages.YapMessages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class AdminCommands implements CommandExecutor, TabCompleter {

    private static final List<String> TROLLS = List.of(
            "smite", "launch", "burn", "rocket", "squash", "blind", "confuse", "slap", "drop");

    private final AdminPlugin plugin;

    public AdminCommands(AdminPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            YapMessages.playersOnly(sender);
            return true;
        }
        if (!player.hasPermission("yapadmin.menu")) {
            YapMessages.noPermission(player, "yapadmin.menu");
            return true;
        }
        if (args.length == 0) {
            plugin.menus().openHub(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        AdminActions actions = plugin.actions();
        return switch (sub) {
            case "reload" -> {
                if (!player.hasPermission("yapadmin.server")) {
                    YapMessages.noPermission(player, "yapadmin.server");
                    yield true;
                }
                plugin.reloadAdminConfig();
                YapMessages.reloaded(player, "YaPAdmin");
                yield true;
            }
            case "give" -> {
                // /yapadmin give <material> [amount] [player]
                if (!player.hasPermission("yapadmin.give")) {
                    YapMessages.noPermission(player, "yapadmin.give");
                    yield true;
                }
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /yapadmin give <material> [amount] [player]");
                    yield true;
                }
                Material mat = Material.matchMaterial(args[1]);
                if (mat == null || !mat.isItem() || mat.isAir()) {
                    player.sendMessage("§cUnknown material: " + args[1]);
                    yield true;
                }
                int amount = 1;
                Player target = player;
                if (args.length >= 3) {
                    try {
                        amount = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        Player named = Bukkit.getPlayerExact(args[2]);
                        if (named == null) {
                            player.sendMessage("§cPlayer offline or bad amount: " + args[2]);
                            yield true;
                        }
                        target = named;
                    }
                }
                if (args.length >= 4) {
                    Player named = Bukkit.getPlayerExact(args[3]);
                    if (named == null) {
                        player.sendMessage("§cPlayer offline: " + args[3]);
                        yield true;
                    }
                    target = named;
                }
                actions.giveItem(player, target, mat, amount);
                yield true;
            }
            case "troll" -> {
                // /yapadmin troll <type> <player>
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /yapadmin troll <type> <player>");
                    yield true;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null || !target.isOnline()) {
                    player.sendMessage("§cPlayer offline: " + args[2]);
                    yield true;
                }
                actions.runTroll(player, target, args[1]);
                yield true;
            }
            case "heal" -> {
                Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : player;
                if (target == null) {
                    player.sendMessage("§cPlayer offline.");
                    yield true;
                }
                actions.heal(player, target);
                yield true;
            }
            case "feed" -> {
                Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : player;
                if (target == null) {
                    player.sendMessage("§cPlayer offline.");
                    yield true;
                }
                actions.feed(player, target);
                yield true;
            }
            case "nv", "nightvision" -> {
                actions.toggleNightVision(player);
                yield true;
            }
            case "clear" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /yapadmin clear <player>");
                    yield true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage("§cPlayer offline: " + args[1]);
                    yield true;
                }
                actions.clearInventory(player, target);
                yield true;
            }
            case "tp", "tpto" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /yapadmin tp <player>");
                    yield true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage("§cPlayer offline: " + args[1]);
                    yield true;
                }
                actions.teleportToPlayer(player, target);
                yield true;
            }
            case "tphere", "bring" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /yapadmin tphere <player>");
                    yield true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage("§cPlayer offline: " + args[1]);
                    yield true;
                }
                actions.teleportHere(player, target);
                yield true;
            }
            case "tpspawn" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /yapadmin tpspawn <player>");
                    yield true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage("§cPlayer offline: " + args[1]);
                    yield true;
                }
                actions.teleportSpawn(player, target);
                yield true;
            }
            case "kick" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /yapadmin kick <player> [reason…]");
                    yield true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage("§cPlayer offline: " + args[1]);
                    yield true;
                }
                String reason = args.length >= 3 ? join(args, 2) : "Staff action";
                actions.kick(player, target, reason);
                yield true;
            }
            case "warn" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /yapadmin warn <player> [reason…]");
                    yield true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage("§cPlayer offline: " + args[1]);
                    yield true;
                }
                String reason = args.length >= 3 ? join(args, 2) : "Staff action";
                actions.warn(player, target, reason);
                yield true;
            }
            case "mute" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /yapadmin mute <player> [reason…]");
                    yield true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage("§cPlayer offline: " + args[1]);
                    yield true;
                }
                String reason = args.length >= 3 ? join(args, 2) : "Staff action";
                actions.muteHour(player, target, reason);
                yield true;
            }
            case "tempban" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /yapadmin tempban <player> [reason…]");
                    yield true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage("§cPlayer offline: " + args[1]);
                    yield true;
                }
                String reason = args.length >= 3 ? join(args, 2) : "Staff action";
                actions.tempbanDay(player, target, reason);
                yield true;
            }
            case "money", "eco" -> {
                if (!player.hasPermission("yapadmin.economy")) {
                    YapMessages.noPermission(player, "yapadmin.economy");
                    yield true;
                }
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /yapadmin money <amount> [player]");
                    yield true;
                }
                int amount;
                try {
                    amount = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage("§cBad amount.");
                    yield true;
                }
                Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : player;
                if (target == null) {
                    player.sendMessage("§cPlayer offline.");
                    yield true;
                }
                actions.giveMoney(player, target, amount);
                yield true;
            }
            case "broadcast", "bc" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /yapadmin broadcast <message…>");
                    yield true;
                }
                actions.broadcast(player, join(args, 1));
                yield true;
            }
            case "chest", "gui" -> {
                plugin.menus().openHub(player);
                yield true;
            }
            default -> {
                player.sendMessage("§cUnknown subcommand. §7Open hub with /yapadmin");
                yield true;
            }
        };
    }

    private static String join(String[] args, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (i > from) {
                sb.append(' ');
            }
            sb.append(args[i]);
        }
        return sb.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], List.of(
                    "reload", "give", "troll", "heal", "feed", "nv", "clear",
                    "tp", "tphere", "tpspawn", "kick", "warn", "mute", "tempban",
                    "money", "broadcast", "chest"));
        }
        if (args.length == 2 && "troll".equalsIgnoreCase(args[0])) {
            return filter(args[1], TROLLS);
        }
        if (args.length == 2 && "give".equalsIgnoreCase(args[0])) {
            String prefix = args[1].toUpperCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (Material m : Material.values()) {
                if (!m.isItem() || m.isAir() || m.name().startsWith("LEGACY_")) {
                    continue;
                }
                if (m.name().startsWith(prefix) && out.size() < 40) {
                    out.add(m.name().toLowerCase(Locale.ROOT));
                }
            }
            return out;
        }
        if (args.length >= 2 && needsPlayer(args[0])) {
            int playerArg = playerArgIndex(args[0]);
            if (args.length == playerArg + 1) {
                return onlineNames(args[playerArg]);
            }
        }
        if (args.length == 3 && "troll".equalsIgnoreCase(args[0])) {
            return onlineNames(args[2]);
        }
        if (args.length == 3 && "give".equalsIgnoreCase(args[0])) {
            return onlineNames(args[2]);
        }
        if (args.length == 4 && "give".equalsIgnoreCase(args[0])) {
            return onlineNames(args[3]);
        }
        return List.of();
    }

    private static boolean needsPlayer(String sub) {
        return switch (sub.toLowerCase(Locale.ROOT)) {
            case "heal", "feed", "clear", "tp", "tphere", "tpspawn",
                 "kick", "warn", "mute", "tempban", "money", "eco" -> true;
            default -> false;
        };
    }

    private static int playerArgIndex(String sub) {
        return switch (sub.toLowerCase(Locale.ROOT)) {
            case "money", "eco" -> 2;
            default -> 1;
        };
    }

    private static List<String> onlineNames(String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(p))
                .sorted()
                .collect(Collectors.toList());
    }

    private static List<String> filter(String prefix, List<String> options) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.startsWith(p)).collect(Collectors.toList());
    }
}
