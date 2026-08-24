package com.yapcore.lagguard;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
            sender.sendMessage("§cNo permission.");
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                plugin.reloadLagGuard();
                sender.sendMessage("§aYaPLagGuard reloaded.");
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
            default -> sender.sendMessage("§7Usage: /yaplagguard status|reload");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("status", "reload")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
        }
        return out;
    }
}
