package com.yapcore.protect.cmd;

import com.yapcore.protect.ProtectConfig;
import com.yapcore.protect.service.ProtectServiceImpl;
import com.yapcore.protect.util.DurationParser;
import com.yapcore.sched.YapSched;
import com.yapcore.messages.YapMessages;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Export / prune handlers for ProtectCommands. */
final class ProtectExportOps {

    private final ProtectServiceImpl service;
    private ProtectConfig config;

    ProtectExportOps(ProtectServiceImpl service, ProtectConfig config) {
        this.service = service;
        this.config = config;
    }

    void setConfig(ProtectConfig config) {
        this.config = config;
    }

    boolean prune(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapprotect.admin")) {
            YapMessages.noPermission(sender, "yapprotect.admin");
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

    boolean export(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapprotect.lookup") && !sender.hasPermission("yapprotect.admin")) {
            YapMessages.noPermission(sender, "yapprotect.lookup");
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
            } else if (ProtectCommandArgParse.looksLikeDuration(token)) {
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
        if (ProtectCommandArgParse.looksLikeDuration(args[2]) || isExportFormat(args[2])) {
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
        if (args.length <= durationIndex || !ProtectCommandArgParse.looksLikeDuration(args[durationIndex])) {
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
}
