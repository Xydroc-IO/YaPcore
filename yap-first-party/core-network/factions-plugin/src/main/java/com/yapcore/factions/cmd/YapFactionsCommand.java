package com.yapcore.factions.cmd;

import com.yapcore.factions.FactionsPlugin;
import com.yapcore.factions.FactionJoinMode;
import com.yapcore.factions.service.FactionServiceImpl;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class YapFactionsCommand implements CommandExecutor, TabCompleter {

    private final FactionsPlugin plugin;
    private final FactionServiceImpl factions;

    public YapFactionsCommand(FactionsPlugin plugin, FactionServiceImpl factions) {
        this.plugin = plugin;
        this.factions = factions;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yapfactions.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length >= 1 && "reload".equalsIgnoreCase(args[0])) {
            plugin.reloadFactions();
            sender.sendMessage("§aYaPFactions reloaded.");
            return true;
        }
        if (args.length >= 2 && "snapshot".equalsIgnoreCase(args[0])) {
            Map<String, Object> snap = factions.dashboardSnapshot();
            if ("json".equalsIgnoreCase(args[1])) {
                sender.sendMessage("YAPFACTIONS_JSON:" + toFlatJson(snap));
            } else {
                sender.sendMessage("§7" + snap);
            }
            return true;
        }
        if (args.length >= 2 && "setpower".equalsIgnoreCase(args[0])) {
            return handleSetPower(sender, args);
        }
        if (args.length >= 2 && "setjoin".equalsIgnoreCase(args[0])) {
            return handleSetJoin(sender, args);
        }
        if (args.length >= 2 && "disband".equalsIgnoreCase(args[0])) {
            return handleForceDisband(sender, args);
        }
        sender.sendMessage("§eUsage: /yapfactions reload|snapshot json|setpower|setjoin|disband");
        return true;
    }

    private boolean handleSetPower(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§eUsage: /yapfactions setpower <faction> <power> [max]");
            return true;
        }
        int power;
        Integer max = null;
        try {
            power = Integer.parseInt(args[2]);
            if (args.length >= 4) {
                max = Integer.parseInt(args[3]);
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid number.");
            return true;
        }
        try {
            factions.adminSetPower(args[1], power, max);
            sender.sendMessage("§aPower updated for §f" + args[1] + ".");
        } catch (Exception e) {
            sender.sendMessage("§c" + e.getMessage());
        }
        return true;
    }

    private boolean handleSetJoin(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§eUsage: /yapfactions setjoin <faction> <open|invite|closed>");
            return true;
        }
        var mode = FactionJoinMode.parse(args[2]);
        if (mode.isEmpty()) {
            sender.sendMessage("§cInvalid join mode.");
            return true;
        }
        try {
            factions.adminSetJoinMode(args[1], mode.get());
            sender.sendMessage("§aJoin mode updated.");
        } catch (Exception e) {
            sender.sendMessage("§c" + e.getMessage());
        }
        return true;
    }

    private boolean handleForceDisband(CommandSender sender, String[] args) {
        try {
            factions.adminForceDisband(args[1]);
            sender.sendMessage("§aFaction disbanded.");
        } catch (Exception e) {
            sender.sendMessage("§c" + e.getMessage());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : List.of("reload", "snapshot", "setpower", "setjoin", "disband")) {
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
