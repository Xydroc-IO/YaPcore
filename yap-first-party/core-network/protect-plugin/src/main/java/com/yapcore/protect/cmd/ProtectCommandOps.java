package com.yapcore.protect.cmd;

import com.yapcore.protect.BlockChangeRecord;
import com.yapcore.protect.ProtectConfig;
import com.yapcore.protect.ProtectLookupCursor;
import com.yapcore.protect.ProtectLookupPage;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Lookup / rollback / restore / prune handlers for ProtectCommands. */
final class ProtectCommandOps {

    private final ProtectServiceImpl service;
    private ProtectConfig config;

    ProtectCommandOps(ProtectServiceImpl service, ProtectConfig config) {
        this.service = service;
        this.config = config;
    }

    void setConfig(ProtectConfig config) {
        this.config = config;
    }

    boolean lookup(CommandSender sender, String[] args) {
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
            sender.sendMessage("§e/yapprotect lookup user <player> [limit] [duration] [--cursor token]");
            return true;
        }
        long durationMs = defaultDurationMs(args, 3);
        if (args.length >= 4 && !looksLikeDuration(args[3]) && !isCursorFlag(args, 3)) {
            try {
                limit = Integer.parseInt(args[3]);
            } catch (NumberFormatException ignored) {
            }
        }
        ProtectLookupCursor cursor = parseCursor(args);
        UUID uuid = Bukkit.getOfflinePlayer(args[2]).getUniqueId();
        long from = now - durationMs;
        int pageLimit = limit;
        service.lookupActorPage(uuid, from, now, pageLimit, cursor)
                .thenAccept(page -> printLookupPage(sender, page, "user " + args[2]
                        + " " + pageLimit + " " + formatDurationHint(durationMs)));
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
        ProtectLookupCursor cursor = parseCursor(args);
        service.lookupBlockPage(world, x, y, z, now - durationMs, now, limit, cursor)
                .thenAccept(page -> printLookupPage(sender, page, "block"));
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
        ProtectLookupCursor cursor = parseCursor(args);
        var loc = player.getLocation();
        int finalRadius = radius;
        service.lookupRadiusPage(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                        radius, now - durationMs, now, limit, cursor)
                .thenAccept(page -> printLookupPage(sender, page,
                        "radius " + finalRadius + " " + formatDurationHint(durationMs)));
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
        service.lookupTimeRangePage(world, now - durationMs, now, limit, parseCursor(args))
                .thenAccept(page -> printLookupPage(sender, page, "time"));
        return true;
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

    private boolean rollbackUser(CommandSender sender, String[] args, long now) {
        if (args.length < 3) {
            sender.sendMessage("§e/yapprotect rollback user <player> [duration]");
            return true;
        }
        UUID uuid = Bukkit.getOfflinePlayer(args[2]).getUniqueId();
        long durationMs = defaultDurationMs(args, 3);
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
        long durationMs = defaultDurationMs(args, 3);
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

    boolean dashLookup(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapprotect.lookup")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("§e/yapprotect dash-lookup user <player> [limit] | radius <blocks> [limit]");
            return true;
        }
        String mode = args[1].toLowerCase(Locale.ROOT);
        int limit = 10;
        if (args.length >= 4) {
            try {
                limit = Integer.parseInt(args[3]);
            } catch (NumberFormatException ignored) {
            }
        }
        long now = System.currentTimeMillis();
        long from = now - TimeUnit.DAYS.toMillis(7);
        ProtectLookupCursor cursor = parseCursor(args);
        try {
            ProtectLookupPage page;
            if ("user".equals(mode)) {
                UUID uuid = Bukkit.getOfflinePlayer(args[2]).getUniqueId();
                page = service.lookupActorPage(uuid, from, now, limit, cursor).get(8, TimeUnit.SECONDS);
            } else if ("radius".equals(mode) && sender instanceof Player player) {
                int radius = Integer.parseInt(args[2]);
                var loc = player.getLocation();
                page = service.lookupRadiusPage(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(),
                                loc.getBlockZ(), radius, from, now, limit, cursor)
                        .get(8, TimeUnit.SECONDS);
            } else {
                sender.sendMessage("§e/yapprotect dash-lookup user <player> [limit] [--cursor t] | radius <blocks> [limit]");
                return true;
            }
            sender.sendMessage("DASH_JSON=" + toDashJson(page));
        } catch (NumberFormatException e) {
            sender.sendMessage("DASH_JSON={\"rows\":[],\"nextCursor\":null,\"hasMore\":false}");
            sender.sendMessage("§cInvalid number.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sender.sendMessage("DASH_JSON={\"rows\":[],\"nextCursor\":null,\"hasMore\":false}");
        } catch (ExecutionException | TimeoutException e) {
            sender.sendMessage("DASH_JSON={\"rows\":[],\"nextCursor\":null,\"hasMore\":false}");
            sender.sendMessage("§cLookup failed: " + e.getMessage());
        }
        return true;
    }

    boolean prune(CommandSender sender, String[] args) {
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

    private static String toDashJson(ProtectLookupPage page) {
        StringBuilder sb = new StringBuilder("{\"rows\":[");
        List<BlockChangeRecord> list = page.rows();
        for (int i = 0; i < list.size(); i++) {
            BlockChangeRecord row = list.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append('{')
                    .append("\"id\":").append(row.id()).append(',')
                    .append("\"serverId\":\"").append(esc(row.serverId())).append("\",")
                    .append("\"changeType\":\"").append(esc(row.changeType())).append("\",")
                    .append("\"actorName\":\"").append(esc(row.actorName())).append("\",")
                    .append("\"world\":\"").append(esc(row.world())).append("\",")
                    .append("\"x\":").append(row.x()).append(',')
                    .append("\"y\":").append(row.y()).append(',')
                    .append("\"z\":").append(row.z()).append(',')
                    .append("\"blockBefore\":\"").append(esc(row.blockBefore())).append("\",")
                    .append("\"blockAfter\":\"").append(esc(row.blockAfter())).append("\",")
                    .append("\"epochMs\":").append(row.epochMs()).append(',')
                    .append("\"rolledBack\":").append(row.rolledBack()).append(',')
                    .append("\"restorable\":").append(row.restorable())
                    .append('}');
        }
        sb.append("],\"hasMore\":").append(page.hasMore()).append(',');
        if (page.nextCursor() != null) {
            sb.append("\"nextCursor\":\"").append(esc(page.nextCursor().encode())).append('"');
        } else {
            sb.append("\"nextCursor\":null");
        }
        return sb.append('}').toString();
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void printLookupPage(CommandSender sender, ProtectLookupPage page, String continueHint) {
        YapSched.global(Bukkit.getPluginManager().getPlugin("YaPProtect"), () -> {
            List<BlockChangeRecord> list = page.rows();
            if (list.isEmpty()) {
                sender.sendMessage("§7No changes found.");
                return;
            }
            sender.sendMessage("§6Protect lookup §7(" + list.size() + (page.hasMore() ? "+" : "") + "):");
            for (BlockChangeRecord row : list) {
                String flags = (row.rolledBack() ? " §crolled-back" : "")
                        + (row.restorable() ? "" : " §8(lookup-only)");
                sender.sendMessage("§7#" + row.id() + " §8[" + row.changeType() + "] §f" + row.actorName()
                        + " §7@ §f" + row.world() + " " + row.x() + "," + row.y() + "," + row.z()
                        + " §7" + row.blockBefore() + " → " + row.blockAfter() + flags);
            }
            if (page.hasMore() && page.nextCursor() != null) {
                sender.sendMessage("§7Next: §e/yapprotect lookup " + continueHint
                        + " --cursor " + page.nextCursor().encode());
            }
        });
    }

    private static ProtectLookupCursor parseCursor(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--cursor".equalsIgnoreCase(args[i])) {
                return ProtectLookupCursor.decode(args[i + 1]).orElse(null);
            }
        }
        return null;
    }

    private static boolean isCursorFlag(String[] args, int index) {
        return index < args.length && "--cursor".equalsIgnoreCase(args[index]);
    }

    private static String formatDurationHint(long durationMs) {
        long days = TimeUnit.MILLISECONDS.toDays(durationMs);
        if (days >= 1) {
            return days + "d";
        }
        long hours = TimeUnit.MILLISECONDS.toHours(durationMs);
        if (hours >= 1) {
            return hours + "h";
        }
        return Math.max(1, TimeUnit.MILLISECONDS.toMinutes(durationMs)) + "m";
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
        if (token == null || token.isBlank() || "--cursor".equalsIgnoreCase(token)) {
            return false;
        }
        char last = token.charAt(token.length() - 1);
        return last == 's' || last == 'm' || last == 'h' || last == 'd' || last == 'w';
    }

    boolean export(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapprotect.lookup") && !sender.hasPermission("yapprotect.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 2) {
            exportHelp(sender);
            return true;
        }
        long now = System.currentTimeMillis();
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "user" -> exportUser(sender, args, now);
            case "time" -> exportTime(sender, args, now);
            default -> {
                exportHelp(sender);
                yield true;
            }
        };
    }

    private boolean exportUser(CommandSender sender, String[] args, long now) {
        if (args.length < 3) {
            sender.sendMessage("§e/yapprotect export user <player> [duration] [csv|json]");
            return true;
        }
        String playerName = args[2];
        UUID uuid = Bukkit.getOfflinePlayer(playerName).getUniqueId();
        long durationMs = TimeUnit.DAYS.toMillis(7);
        String format = "csv";
        for (int i = 3; i < args.length; i++) {
            String token = args[i];
            if (isExportFormat(token)) {
                format = token.toLowerCase(Locale.ROOT);
            } else if (looksLikeDuration(token)) {
                try {
                    durationMs = DurationParser.parseToMillis(token);
                } catch (IllegalArgumentException e) {
                    sender.sendMessage("§cBad duration: " + token);
                    return true;
                }
            }
        }
        String finalFormat = format;
        long from = now - durationMs;
        service.exportActor(uuid, from, now).thenAccept(rows ->
                writeExport(sender, rows, finalFormat, "user-" + sanitizeFileToken(playerName)));
        return true;
    }

    private boolean exportTime(CommandSender sender, String[] args, long now) {
        // /yapprotect export time [world] <duration> [csv|json]
        if (args.length < 3) {
            sender.sendMessage("§e/yapprotect export time [world] <duration> [csv|json]");
            return true;
        }
        String world;
        int durationIndex;
        if (looksLikeDuration(args[2]) || isExportFormat(args[2])) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§e/yapprotect export time <world> <duration> [csv|json]");
                return true;
            }
            world = player.getWorld().getName();
            durationIndex = 2;
        } else {
            world = args[2];
            durationIndex = 3;
        }
        if (args.length <= durationIndex || !looksLikeDuration(args[durationIndex])) {
            sender.sendMessage("§e/yapprotect export time [world] <duration> [csv|json]");
            return true;
        }
        long durationMs;
        try {
            durationMs = DurationParser.parseToMillis(args[durationIndex]);
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§cBad duration: " + args[durationIndex]);
            return true;
        }
        String format = "csv";
        for (int i = durationIndex + 1; i < args.length; i++) {
            if (isExportFormat(args[i])) {
                format = args[i].toLowerCase(Locale.ROOT);
            }
        }
        String finalFormat = format;
        String finalWorld = world;
        service.exportTimeRange(world, now - durationMs, now).thenAccept(rows ->
                writeExport(sender, rows, finalFormat, "time-" + sanitizeFileToken(finalWorld)));
        return true;
    }

    private void writeExport(CommandSender sender, List<com.yapcore.protect.model.ProtectChange> rows,
                             String format, String namePrefix) {
        var plugin = Bukkit.getPluginManager().getPlugin("YaPProtect");
        if (plugin == null) {
            sender.sendMessage("§cYaPProtect not loaded.");
            return;
        }
        try {
            java.nio.file.Path dir = plugin.getDataFolder().toPath().resolve("exports");
            java.nio.file.Files.createDirectories(dir);
            String stamp = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            boolean json = "json".equalsIgnoreCase(format);
            String fileName = namePrefix + "-" + stamp + (json ? ".json" : ".csv");
            java.nio.file.Path out = dir.resolve(fileName);
            String body = json
                    ? com.yapcore.protect.util.ProtectExportFormatter.toJson(rows)
                    : com.yapcore.protect.util.ProtectExportFormatter.toCsv(rows);
            java.nio.file.Files.writeString(out, body, java.nio.charset.StandardCharsets.UTF_8);
            YapSched.global(plugin, () -> sender.sendMessage(
                    "§aExported §f" + rows.size() + " §arow(s) → §fplugins/YaPProtect/exports/" + fileName));
        } catch (Exception e) {
            YapSched.global(plugin, () -> sender.sendMessage("§cExport failed: " + e.getMessage()));
        }
    }

    private static boolean isExportFormat(String token) {
        return "csv".equalsIgnoreCase(token) || "json".equalsIgnoreCase(token);
    }

    private static String sanitizeFileToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return "unknown";
        }
        String cleaned = raw.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.length() > 48 ? cleaned.substring(0, 48) : cleaned;
    }

    private void exportHelp(CommandSender sender) {
        sender.sendMessage("§e/yapprotect export user <player> [duration] [csv|json]");
        sender.sendMessage("§e/yapprotect export time [world] <duration> [csv|json]");
    }

    private void lookupHelp(CommandSender sender) {
        sender.sendMessage("§e/yapprotect lookup user <player> [limit] [duration] [--cursor token]");
        sender.sendMessage("§e/yapprotect lookup block [x y z] [world] [duration] [--cursor token]");
        sender.sendMessage("§e/yapprotect lookup radius <blocks> [duration] [--cursor token]");
        sender.sendMessage("§e/yapprotect lookup time [world] <duration> [--cursor token]");
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
