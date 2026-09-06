package com.yapcore.lagguard;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.yapcore.messages.YapMessages;

public final class LagGuardCommands implements CommandExecutor, TabCompleter {

    private final LagGuardPlugin plugin;
    private LagGuardConfig config;
    private final ChunkBudgetTracker tracker;

    public LagGuardCommands(LagGuardPlugin plugin, LagGuardConfig config, ChunkBudgetTracker tracker) {
        this.plugin = plugin;
        this.config = config;
        this.tracker = tracker;
    }

    public void setConfig(LagGuardConfig config) {
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yaplagguard.admin")) {
            YapMessages.noPermission(sender, "yaplagguard.admin");
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                plugin.reloadLagGuard();
                YapMessages.reloaded(sender, "YaPLagGuard");
            }
            case "status" -> {
                sender.sendMessage("§aYaPLagGuard §7enabled=" + config.enabled()
                        + " entities/chunk≤" + config.maxEntitiesPerChunk()
                        + " tnt≤" + config.maxPrimedTntPerChunk());
                sender.sendMessage("§7trips=" + tracker.trips()
                        + " entitiesCancelled=" + tracker.entitiesCancelled()
                        + " tntCancelled=" + tracker.tntCancelled()
                        + " hopper=" + tracker.hopperThrottled()
                        + " redstone=" + tracker.redstoneThrottled());
            }
            case "top" -> {
                int n = 10;
                if (args.length >= 2) {
                    try {
                        n = Math.max(1, Math.min(50, Integer.parseInt(args[1])));
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§cUsage: /yaplagguard top [n]");
                        return true;
                    }
                }
                var top = tracker.topChunks(n);
                if (top.isEmpty()) {
                    sender.sendMessage("§7No chunk trips recorded yet.");
                    return true;
                }
                sender.sendMessage("§aHot chunks (top " + top.size() + "):");
                int i = 1;
                for (var c : top) {
                    sender.sendMessage("§7" + i++ + ". §f" + c.world() + " §7" + c.cx() + "," + c.cz()
                            + " §atrips=§f" + c.trips());
                }
            }
            default -> sender.sendMessage("§7Usage: /yaplagguard status|reload|top [n]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("status", "reload", "top")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
        }
        return out;
    }
}
