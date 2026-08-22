package com.yapcore.protect.cmd;

import com.yapcore.protect.BlockChangeRecord;
import com.yapcore.protect.ProtectConfig;
import com.yapcore.protect.service.ProtectServiceImpl;
import com.yapcore.protect.util.DurationParser;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class ProtectCommands implements CommandExecutor, TabCompleter {

    private final ProtectServiceImpl service;
    private ProtectConfig config;

    public ProtectCommands(ProtectServiceImpl service, ProtectConfig config) {
        this.service = service;
        this.config = config;
    }

    public void setConfig(ProtectConfig config) {
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return status(sender);
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(sender);
            case "status" -> status(sender);
            case "lookup" -> lookup(sender, args);
            case "dash-lookup" -> dashLookup(sender, args);
            case "rollback" -> rollback(sender, args);
            case "prune" -> prune(sender, args);
            default -> {
                help(sender);
                yield true;
            }
        };
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("yapprotect.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        var plugin = Bukkit.getPluginManager().getPlugin("YaPProtect");
        if (plugin instanceof com.yapcore.protect.ProtectPlugin protect) {
            try {
                protect.reloadProtect();
                sender.sendMessage("§aYaPProtect reloaded.");
            } catch (java.sql.SQLException e) {
                sender.sendMessage("§cReload failed: " + e.getMessage());
            }
        }
        return true;
    }

    private boolean status(CommandSender sender) {
        if (!sender.hasPermission("yapprotect.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        service.countAll().thenAccept(count -> YapSched.global(
                Bukkit.getPluginManager().getPlugin("YaPProtect"),
                () -> sender.sendMessage("§aYaPProtect §7— logging=§f" + service.isLogging()
                        + " §7rows=§f" + count + " §7server=§f" + config.serverId()
                        + " §7max-radius=§f" + config.maxRollbackRadius())));
        return true;
    }

    private boolean lookup(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapprotect.lookup")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 2) {
            lookupHelp(sender);
            return true;
        }
        long now = System.currentTimeMillis();
        int limit = config.maxLookupLimit();
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "user" -> lookupUser(sender, args, now, limit);
            case "block" -> lookupBlock(sender, args, now, limit);
            case "radius" -> lookupRadius(sender, args, now, limit);
            case "time" -> lookupTime(sender, args, now, limit);
            default -> {
                lookupHelp(sender);
                yield true;
            }
        };
    }

    private boolean lookupUser(CommandSender sender, String[] args, long now, int limit) {
        if (args.length < 3) {
            sender.sendMessage("§e/yapprotect lookup user <player> [limit] [duration]");
            return true;
        }
        long durationMs = defaultDurationMs(args, 3);
        if (args.length >= 4 && !looksLikeDuration(args[3])) {
            try {
                limit = Integer.parseInt(args[3]);
            } catch (NumberFormatException ignored) {
            }
        }
        UUID uuid = Bukkit.getOfflinePlayer(args[2]).getUniqueId();
        long from = now - durationMs;
        service.lookupActor(uuid, from, now, limit).thenAccept(list -> printLookup(sender, list));
        return true;
    }

    private boolean lookupBlock(CommandSender sender, String[] args, long now, int limit) {
        int x;
        int y;
        int z;
        String world;
        if (sender instanceof Player player && args.length < 4) {
            x = player.getLocation().getBlockX();
            y = player.getLocation().getBlockY();
            z = player.getLocation().getBlockZ();
            world = player.getWorld().getName();
        } else if (args.length >= 5) {
            try {
                x = Integer.parseInt(args[2]);
                y = Integer.parseInt(args[3]);
                z = Integer.parseInt(args[4]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cInvalid coordinates.");
                return true;
            }
            world = args.length >= 6 ? args[5] : (sender instanceof Player p ? p.getWorld().getName() : "world");
        } else {
            sender.sendMessage("§e/yapprotect lookup block [x y z] [world] [duration]");
            return true;
        }
        long durationMs = defaultDurationMs(args, args.length >= 6 ? 6 : args.length);
        service.lookupBlock(world, x, y, z, now - durationMs, now, limit)
                .thenAccept(list -> printLookup(sender, list));
        return true;
    }

    private boolean lookupRadius(CommandSender sender, String[] args, long now, int limit) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only for radius lookup.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("§e/yapprotect lookup radius <blocks> [duration]");
            return true;
        }
        int radius;
        try {
            radius = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid radius.");
            return true;
        }
        if (radius > config.maxRollbackRadius()) {
            sender.sendMessage("§cRadius capped at §f" + config.maxRollbackRadius());
            radius = config.maxRollbackRadius();
        }
        long durationMs = defaultDurationMs(args, 3);
        var loc = player.getLocation();
        service.lookupRadius(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                        radius, now - durationMs, now, limit)
                .thenAccept(list -> printLookup(sender, list));
        return true;
    }

    private boolean lookupTime(CommandSender sender, String[] args, long now, int limit) {
        String world;
        int durationArgIndex;
        if (sender instanceof Player player) {
            world = player.getWorld().getName();
            durationArgIndex = 2;
        } else if (args.length >= 4) {
            world = args[2];
            durationArgIndex = 3;
        } else {
            sender.sendMessage("§e/yapprotect lookup time <duration> | time <world> <duration>");
            return true;
        }
        if (args.length <= durationArgIndex) {
            sender.sendMessage("§e/yapprotect lookup time <duration>");
            return true;
        }
        long durationMs;
        try {
            durationMs = DurationParser.parseToMillis(args[durationArgIndex]);
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§cBad duration: " + args[durationArgIndex] + " §7(try 30m, 2h, 7d)");
            return true;
        }
        service.lookupTimeRange(world, now - durationMs, now, limit)
                .thenAccept(list -> printLookup(sender, list));
        return true;
    }

    private boolean rollback(CommandSender sender, String[] args) {
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
        long durationMs = defaultDurationMs(args, 3);
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

    private boolean dashLookup(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapprotect.lookup")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 3 || !"user".equalsIgnoreCase(args[1])) {
            sender.sendMessage("§e/yapprotect dash-lookup user <player> [limit]");
            return true;
        }
        String playerName = args[2];
        int limit = 10;
        if (args.length >= 4) {
            try {
                limit = Integer.parseInt(args[3]);
            } catch (NumberFormatException ignored) {
            }
        }
        UUID uuid = Bukkit.getOfflinePlayer(playerName).getUniqueId();
        long now = System.currentTimeMillis();
        long from = now - TimeUnit.DAYS.toMillis(7);
        try {
            List<BlockChangeRecord> list = service.lookupActor(uuid, from, now, limit)
                    .get(8, TimeUnit.SECONDS);
            sender.sendMessage("DASH_JSON=" + toDashJson(list));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sender.sendMessage("DASH_JSON=[]");
        } catch (ExecutionException | TimeoutException e) {
            sender.sendMessage("DASH_JSON=[]");
            sender.sendMessage("§cLookup failed: " + e.getMessage());
        }
        return true;
    }

    private static String toDashJson(List<BlockChangeRecord> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            BlockChangeRecord row = list.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append('{')
                    .append("\"id\":").append(row.id()).append(',')
                    .append("\"changeType\":\"").append(esc(row.changeType())).append("\",")
                    .append("\"actorName\":\"").append(esc(row.actorName())).append("\",")
                    .append("\"world\":\"").append(esc(row.world())).append("\",")
                    .append("\"x\":").append(row.x()).append(',')
                    .append("\"y\":").append(row.y()).append(',')
                    .append("\"z\":").append(row.z()).append(',')
                    .append("\"blockBefore\":\"").append(esc(row.blockBefore())).append("\",")
                    .append("\"blockAfter\":\"").append(esc(row.blockAfter())).append("\",")
                    .append("\"epochMs\":").append(row.epochMs())
                    .append('}');
        }
        return sb.append(']').toString();
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void printLookup(CommandSender sender, List<BlockChangeRecord> list) {
        YapSched.global(Bukkit.getPluginManager().getPlugin("YaPProtect"), () -> {
            if (list.isEmpty()) {
                sender.sendMessage("§7No changes found.");
                return;
            }
            sender.sendMessage("§6Protect lookup §7(" + list.size() + "):");
            for (BlockChangeRecord row : list) {
                sender.sendMessage("§7#" + row.id() + " §8[" + row.changeType() + "] §f" + row.actorName()
                        + " §7@ §f" + row.world() + " " + row.x() + "," + row.y() + "," + row.z()
                        + " §7" + row.blockBefore() + " → " + row.blockAfter());
            }
        });
    }

    private boolean prune(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapprotect.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        int days = config.pruneDays();
        if (args.length >= 2) {
            try {
                days = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cInvalid days.");
                return true;
            }
        }
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days);
        int finalDays = days;
        service.pruneBefore(cutoff).thenAccept(deleted ->
                YapSched.global(Bukkit.getPluginManager().getPlugin("YaPProtect"),
                        () -> sender.sendMessage("§aPruned §f" + deleted + " §arows older than §f"
                                + finalDays + " §adays.")));
        return true;
    }

    private static long defaultDurationMs(String[] args, int durationIndex) {
        if (args.length > durationIndex && looksLikeDuration(args[durationIndex])) {
            try {
                return DurationParser.parseToMillis(args[durationIndex]);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return TimeUnit.DAYS.toMillis(7);
    }

    private static boolean looksLikeDuration(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        char last = token.charAt(token.length() - 1);
        return last == 's' || last == 'm' || last == 'h' || last == 'd' || last == 'w';
    }

    private void lookupHelp(CommandSender sender) {
        sender.sendMessage("§e/yapprotect lookup user <player> [limit] [duration]");
        sender.sendMessage("§e/yapprotect lookup block [x y z] [world] [duration]");
        sender.sendMessage("§e/yapprotect lookup radius <blocks> [duration]");
        sender.sendMessage("§e/yapprotect lookup time [world] <duration>");
    }

    private void rollbackHelp(CommandSender sender) {
        sender.sendMessage("§e/yapprotect rollback <id> [id...]");
        sender.sendMessage("§e/yapprotect rollback radius <blocks> [duration]");
        sender.sendMessage("§e/yapprotect rollback time [world] <duration>");
    }

    private void help(CommandSender sender) {
        sender.sendMessage("§e/yapprotect status|reload|lookup|rollback|prune");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("status", "reload", "lookup", "rollback", "prune"), args[0]);
        }
        if (args.length == 2 && "lookup".equalsIgnoreCase(args[0])) {
            return filter(List.of("user", "block", "radius", "time"), args[1]);
        }
        if (args.length == 2 && "rollback".equalsIgnoreCase(args[0])) {
            return filter(List.of("radius", "time"), args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.startsWith(lower)).toList();
    }
}
