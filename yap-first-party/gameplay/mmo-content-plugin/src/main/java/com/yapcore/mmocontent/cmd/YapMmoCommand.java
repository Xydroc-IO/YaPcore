package com.yapcore.mmocontent.cmd;

import com.yapcore.mmocontent.MmoContentPlugin;
import com.yapcore.mmocontent.db.TeleportUnlockRepository;
import com.yapcore.mmocontent.service.MmoSnapshotServiceImpl;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class YapMmoCommand implements CommandExecutor, TabCompleter {

    private final MmoContentPlugin plugin;
    private final MmoSnapshotServiceImpl snapshot;
    private final TeleportUnlockRepository teleports;

    public YapMmoCommand(MmoContentPlugin plugin,
                         MmoSnapshotServiceImpl snapshot,
                         TeleportUnlockRepository teleports) {
        this.plugin = plugin;
        this.snapshot = snapshot;
        this.teleports = teleports;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§e/yapmmo reload|snapshot [json]|givemoney|unlockteleport");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                if (!sender.hasPermission("yapmmo.admin")) {
                    sender.sendMessage("§cNo permission.");
                    return true;
                }
                plugin.reloadContent();
                sender.sendMessage("§aYaPMmoContent reloaded.");
            }
            case "snapshot" -> {
                if (!sender.hasPermission("yapmmo.admin")) {
                    sender.sendMessage("§cNo permission.");
                    return true;
                }
                Map<String, Object> snap = snapshot.snapshot();
                if (args.length >= 2 && "json".equalsIgnoreCase(args[1])) {
                    sender.sendMessage("YAPMMO_JSON:" + toJson(snap));
                } else {
                    sender.sendMessage("§6MMO snapshot: bosses=" + snap.get("bossCount")
                            + " areas=" + snap.get("areaCount")
                            + " skills=" + snap.get("skillCount"));
                }
            }
            case "givemoney" -> {
                if (args.length < 3) {
                    sender.sendMessage("Usage: /yapmmo givemoney <player> <amount>");
                    return true;
                }
                handleGiveMoney(sender, args[1], args[2]);
            }
            case "unlockteleport" -> {
                if (args.length < 3) {
                    sender.sendMessage("Usage: /yapmmo unlockteleport <player> <id>");
                    return true;
                }
                handleUnlockTeleport(sender, args[1], args[2]);
            }
            default -> sender.sendMessage("§e/yapmmo reload|snapshot [json]|givemoney|unlockteleport");
        }
        return true;
    }

    private void handleGiveMoney(CommandSender sender, String playerName, String amountRaw) {
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            sender.sendMessage("§cPlayer must be online.");
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(amountRaw);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid amount.");
            return;
        }
        if (amount < 0 || Double.isNaN(amount) || Double.isInfinite(amount)) {
            sender.sendMessage("§cInvalid amount.");
            return;
        }
        YapSched.global(plugin, () -> {
            var economy = Bukkit.getServicesManager().load(com.yapcore.playerdata.PlayerDataService.class);
            if (economy == null || !economy.economyEnabled()) {
                sender.sendMessage("§cEconomy unavailable — YaPPlayerData required.");
                return;
            }
            var next = economy.deposit(target.getUniqueId(), amount);
            if (next.isEmpty()) {
                sender.sendMessage("§cCould not deposit.");
                return;
            }
            sender.sendMessage("§aGave " + economy.formatMoney(amount) + " to " + target.getName()
                    + " (now " + economy.formatMoney(next.get()) + ")");
            target.sendMessage("§aYou received §f" + economy.formatMoney(amount) + "§a.");
        });
    }

    private void handleUnlockTeleport(CommandSender sender, String playerName, String unlockId) {
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            sender.sendMessage("§cPlayer must be online.");
            return;
        }
        YapSched.async(plugin, () -> {
            try {
                teleports.unlock(target.getUniqueId(), unlockId);
                YapSched.entity(plugin, target, () ->
                        target.sendMessage("§aTeleport unlocked: §f" + unlockId));
            } catch (Exception e) {
                YapSched.global(plugin, () -> sender.sendMessage("§cUnlock failed."));
            }
        });
    }

    private static String toJson(Map<String, Object> snap) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : snap.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escape(e.getKey())).append("\":");
            sb.append(valueJson(e.getValue()));
        }
        sb.append('}');
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static String valueJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((k, v) -> copy.put(String.valueOf(k), v));
            return toJson(copy);
        }
        if (value instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : list) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(valueJson(item));
            }
            sb.append(']');
            return sb.toString();
        }
        return "\"" + escape(String.valueOf(value)) + "\"";
    }

    private static String escape(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return prefix(List.of("reload", "snapshot", "givemoney", "unlockteleport"), args[0]);
        }
        if (args.length == 2 && "snapshot".equalsIgnoreCase(args[0])) {
            return prefix(List.of("json"), args[1]);
        }
        return List.of();
    }

    private static List<String> prefix(List<String> options, String partial) {
        String p = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }
}
