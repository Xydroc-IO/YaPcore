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

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Lookup / dash-lookup handlers for ProtectCommands. */
final class ProtectLookupOps {

    private final ProtectServiceImpl service;
    private ProtectConfig config;

    ProtectLookupOps(ProtectServiceImpl service, ProtectConfig config) {
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
        long durationMs = ProtectCommandArgParse.defaultDurationMs(args, 3);
        if (args.length >= 4 && !ProtectCommandArgParse.looksLikeDuration(args[3])
                && !ProtectCommandArgParse.isCursorFlag(args, 3)) {
            try {
                limit = Integer.parseInt(args[3]);
            } catch (NumberFormatException ignored) {
            }
        }
        ProtectLookupCursor cursor = ProtectCommandArgParse.parseCursor(args);
        UUID uuid = Bukkit.getOfflinePlayer(args[2]).getUniqueId();
        long from = now - durationMs;
        int pageLimit = limit;
        service.lookupActorPage(uuid, from, now, pageLimit, cursor)
                .thenAccept(page -> printLookupPage(sender, page, "user " + args[2]
                        + " " + pageLimit + " " + ProtectCommandArgParse.formatDurationHint(durationMs)));
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
        long durationMs = ProtectCommandArgParse.defaultDurationMs(args, args.length >= 6 ? 6 : args.length);
        ProtectLookupCursor cursor = ProtectCommandArgParse.parseCursor(args);
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
        long durationMs = ProtectCommandArgParse.defaultDurationMs(args, 3);
        ProtectLookupCursor cursor = ProtectCommandArgParse.parseCursor(args);
        var loc = player.getLocation();
        int finalRadius = radius;
        service.lookupRadiusPage(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                        radius, now - durationMs, now, limit, cursor)
                .thenAccept(page -> printLookupPage(sender, page,
                        "radius " + finalRadius + " " + ProtectCommandArgParse.formatDurationHint(durationMs)));
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
        service.lookupTimeRangePage(world, now - durationMs, now, limit, ProtectCommandArgParse.parseCursor(args))
                .thenAccept(page -> printLookupPage(sender, page, "time"));
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
        ProtectLookupCursor cursor = ProtectCommandArgParse.parseCursor(args);
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

    private void lookupHelp(CommandSender sender) {
        sender.sendMessage("§e/yapprotect lookup user <player> [limit] [duration] [--cursor token]");
        sender.sendMessage("§e/yapprotect lookup block [x y z] [world] [duration] [--cursor token]");
        sender.sendMessage("§e/yapprotect lookup radius <blocks> [duration] [--cursor token]");
        sender.sendMessage("§e/yapprotect lookup time [world] <duration> [--cursor token]");
    }
}
