package com.yapcore.guilds.cmd;

import com.yapcore.guilds.GuildJoinMode;
import com.yapcore.guilds.GuildsPlugin;
import com.yapcore.guilds.service.GuildServiceImpl;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class YapGuildsCommand implements CommandExecutor, TabCompleter {

    private final GuildsPlugin plugin;
    private final GuildServiceImpl guilds;

    public YapGuildsCommand(GuildsPlugin plugin, GuildServiceImpl guilds) {
        this.plugin = plugin;
        this.guilds = guilds;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yapguilds.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length >= 1 && "reload".equalsIgnoreCase(args[0])) {
            plugin.reloadGuilds();
            sender.sendMessage("§aYaPGuilds reloaded.");
            return true;
        }
        if (args.length >= 2 && "snapshot".equalsIgnoreCase(args[0])) {
            Map<String, Object> snap = guilds.dashboardSnapshot();
            if ("json".equalsIgnoreCase(args[1])) {
                sender.sendMessage("YAPGUILDS_JSON:" + toFlatJson(snap));
            } else {
                sender.sendMessage("§7" + snap);
            }
            return true;
        }
        if (args.length >= 3 && "setlevel".equalsIgnoreCase(args[0])) {
            return handleSetLevel(sender, args);
        }
        if (args.length >= 2 && "disband".equalsIgnoreCase(args[0])) {
            return handleForceDisband(sender, args);
        }
        sender.sendMessage("§eUsage: /yapguilds reload|snapshot json|setlevel|disband");
        return true;
    }

    private boolean handleSetLevel(CommandSender sender, String[] args) {
        int level;
        Long xp = null;
        try {
            level = Integer.parseInt(args[2]);
            if (args.length >= 4) {
                xp = Long.parseLong(args[3]);
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid number.");
            return true;
        }
        try {
            guilds.adminSetLevel(args[1], level, xp);
            sender.sendMessage("§aGuild level updated for §f" + args[1] + ".");
        } catch (Exception e) {
            sender.sendMessage("§c" + e.getMessage());
        }
        return true;
    }

    private boolean handleForceDisband(CommandSender sender, String[] args) {
        try {
            guilds.adminForceDisband(args[1]);
            sender.sendMessage("§aGuild disbanded.");
        } catch (Exception e) {
            sender.sendMessage("§c" + e.getMessage());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : List.of("reload", "snapshot", "setlevel", "disband")) {
                if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(sub);
                }
            }
        } else if (args.length == 2 && "snapshot".equalsIgnoreCase(args[0])) {
            if ("json".startsWith(args[1].toLowerCase(Locale.ROOT))) {
                out.add("json");
            }
        }
        return out;
    }

    private static String toFlatJson(Map<String, Object> snap) {
        return snap.entrySet().stream()
                .map(e -> e.getKey() + "=" + String.valueOf(e.getValue()))
                .collect(Collectors.joining("|"));
    }
}
