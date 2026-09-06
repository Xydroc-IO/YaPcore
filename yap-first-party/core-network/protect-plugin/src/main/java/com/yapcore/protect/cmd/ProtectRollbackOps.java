package com.yapcore.protect.cmd;

import com.yapcore.protect.service.ProtectServiceImpl;
import com.yapcore.protect.util.DurationParser;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Rollback / restore handlers for ProtectCommands. */
final class ProtectRollbackOps {

    private final ProtectServiceImpl service;

    ProtectRollbackOps(ProtectServiceImpl service) {
        this.service = service;
    }

    boolean rollback(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapprotect.rollback")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 2) {
            rollbackHelp(sender);
            return true;
        }
        long now = System.currentTimeMillis();
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "radius" -> rollbackRadius(sender, args, now);
            case "time" -> rollbackTime(sender, args, now);
            case "user" -> rollbackUser(sender, args, now);
            default -> rollbackIds(sender, args);
        };
    }

    private boolean rollbackIds(CommandSender sender, String[] args) {
        List<Long> ids = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            try {
                ids.add(Long.parseLong(args[i]));
            } catch (NumberFormatException e) {
                sender.sendMessage("§cInvalid id: " + args[i]);
                return true;
            }
        }
        service.rollbackChanges(ids).thenAccept(count ->
                YapSched.global(Bukkit.getPluginManager().getPlugin("YaPProtect"),
                        () -> sender.sendMessage("§aRollback applied to §f" + count + " §achange(s).")));
        return true;
    }

    private boolean rollbackRadius(CommandSender sender, String[] args, long now) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only for radius rollback.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("§e/yapprotect rollback radius <blocks> [duration]");
            return true;
        }
        int radius;
        try {
            radius = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid radius.");
            return true;
        }
        long durationMs = ProtectCommandArgParse.defaultDurationMs(args, 3);
        var loc = player.getLocation();
        service.rollbackRadius(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                        radius, now - durationMs, now)
                .thenAccept(count -> YapSched.global(Bukkit.getPluginManager().getPlugin("YaPProtect"),
                        () -> sender.sendMessage("§aRadius rollback applied to §f" + count + " §achange(s).")));
        return true;
    }

    private boolean rollbackTime(CommandSender sender, String[] args, long now) {
        String world;
        int durationArgIndex;
        if (sender instanceof Player player) {
            world = player.getWorld().getName();
            durationArgIndex = 2;
        } else if (args.length >= 4) {
            world = args[2];
            durationArgIndex = 3;
        } else {
            sender.sendMessage("§e/yapprotect rollback time <duration> | time <world> <duration>");
            return true;
        }
        if (args.length <= durationArgIndex) {
            sender.sendMessage("§e/yapprotect rollback time <duration>");
            return true;
        }
        long durationMs;
        try {
            durationMs = DurationParser.parseToMillis(args[durationArgIndex]);
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§cBad duration: " + args[durationArgIndex]);
            return true;
        }
        service.rollbackTimeRange(world, now - durationMs, now)
                .thenAccept(count -> YapSched.global(Bukkit.getPluginManager().getPlugin("YaPProtect"),
                        () -> sender.sendMessage("§aTime rollback applied to §f" + count + " §achange(s).")));
        return true;
    }

    private boolean rollbackUser(CommandSender sender, String[] args, long now) {
        if (args.length < 3) {
            sender.sendMessage("§e/yapprotect rollback user <player> [duration]");
            return true;
        }
        UUID uuid = Bukkit.getOfflinePlayer(args[2]).getUniqueId();
        long durationMs = ProtectCommandArgParse.defaultDurationMs(args, 3);
        service.rollbackUser(uuid, now - durationMs, now)
                .thenAccept(count -> YapSched.global(Bukkit.getPluginManager().getPlugin("YaPProtect"),
                        () -> sender.sendMessage("§aUser rollback applied to §f" + count + " §achange(s).")));
        return true;
    }

    boolean restore(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapprotect.rollback")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 2) {
            restoreHelp(sender);
            return true;
        }
        long now = System.currentTimeMillis();
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "time" -> restoreTime(sender, args, now);
            case "user" -> restoreUser(sender, args, now);
            default -> restoreIds(sender, args);
        };
    }

    private boolean restoreIds(CommandSender sender, String[] args) {
        List<Long> ids = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            try {
                ids.add(Long.parseLong(args[i]));
            } catch (NumberFormatException e) {
                sender.sendMessage("§cInvalid id: " + args[i]);
                return true;
            }
        }
        service.restoreChanges(ids).thenAccept(count ->
                YapSched.global(Bukkit.getPluginManager().getPlugin("YaPProtect"),
                        () -> sender.sendMessage("§aRestore applied to §f" + count + " §achange(s).")));
        return true;
    }

    private boolean restoreUser(CommandSender sender, String[] args, long now) {
        if (args.length < 3) {
            sender.sendMessage("§e/yapprotect restore user <player> [duration]");
            return true;
        }
        UUID uuid = Bukkit.getOfflinePlayer(args[2]).getUniqueId();
        long durationMs = ProtectCommandArgParse.defaultDurationMs(args, 3);
        service.restoreUser(uuid, now - durationMs, now)
                .thenAccept(count -> YapSched.global(Bukkit.getPluginManager().getPlugin("YaPProtect"),
                        () -> sender.sendMessage("§aUser restore applied to §f" + count + " §achange(s).")));
        return true;
    }

    private boolean restoreTime(CommandSender sender, String[] args, long now) {
        String world;
        int durationArgIndex;
        if (sender instanceof Player player) {
            world = player.getWorld().getName();
            durationArgIndex = 2;
        } else if (args.length >= 4) {
            world = args[2];
            durationArgIndex = 3;
        } else {
            sender.sendMessage("§e/yapprotect restore time <duration> | time <world> <duration>");
            return true;
        }
        if (args.length <= durationArgIndex) {
            sender.sendMessage("§e/yapprotect restore time <duration>");
            return true;
        }
        long durationMs;
        try {
            durationMs = DurationParser.parseToMillis(args[durationArgIndex]);
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§cBad duration: " + args[durationArgIndex]);
            return true;
        }
        service.restoreTimeRange(world, now - durationMs, now)
                .thenAccept(count -> YapSched.global(Bukkit.getPluginManager().getPlugin("YaPProtect"),
                        () -> sender.sendMessage("§aTime restore applied to §f" + count + " §achange(s).")));
        return true;
    }

    private void rollbackHelp(CommandSender sender) {
        sender.sendMessage("§e/yapprotect rollback <id> [id...]");
        sender.sendMessage("§e/yapprotect rollback radius <blocks> [duration]");
        sender.sendMessage("§e/yapprotect rollback time [world] <duration>");
        sender.sendMessage("§e/yapprotect rollback user <player> [duration]");
    }

    private void restoreHelp(CommandSender sender) {
        sender.sendMessage("§e/yapprotect restore <id> [id...]");
        sender.sendMessage("§e/yapprotect restore time [world] <duration>");
        sender.sendMessage("§e/yapprotect restore user <player> [duration]");
    }
}
