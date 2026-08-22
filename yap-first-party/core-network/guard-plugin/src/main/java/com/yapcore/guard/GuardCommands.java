package com.yapcore.guard;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GuardCommands implements CommandExecutor, TabCompleter {

    private final GuardPlugin plugin;
    private GuardConfig config;
    private final ViolationTracker tracker;
    private final GuardServiceImpl service;

    public GuardCommands(GuardPlugin plugin, GuardConfig config, ViolationTracker tracker,
                         GuardServiceImpl service) {
        this.plugin = plugin;
        this.config = config;
        this.tracker = tracker;
        this.service = service;
    }

    public void setConfig(GuardConfig config) {
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yapguard.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§e/yapguard status|reload|alerts [on|off]");
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> status(sender, args);
            case "reload" -> reload(sender);
            case "alerts" -> alerts(sender, args);
            default -> {
                sender.sendMessage("§e/yapguard status|reload|alerts [on|off]");
                yield true;
            }
        };
    }

    private boolean status(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found.");
                return true;
            }
            int count = service.violationCount(target.getUniqueId());
            sender.sendMessage("§a" + target.getName() + " violations: §f" + count);
            return true;
        }
        sender.sendMessage("§aYaPGuard §7— fly=" + on(config.flyEnabled())
                + " speed=" + on(config.speedEnabled())
                + " reach=" + on(config.reachEnabled())
                + " scaffold=" + on(config.scaffoldEnabled())
                + " kick-after=" + config.maxViolationsBeforeKick()
                + " alerts=" + on(config.alertsEnabled()));
        sender.sendMessage("§7Online tracked: §f" + Bukkit.getOnlinePlayers().size());
        return true;
    }

    private boolean reload(CommandSender sender) {
        plugin.reloadGuard();
        sender.sendMessage("§aYaPGuard reloaded.");
        return true;
    }

    private boolean alerts(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            boolean on = "on".equalsIgnoreCase(args[1]) || "true".equalsIgnoreCase(args[1]);
            boolean off = "off".equalsIgnoreCase(args[1]) || "false".equalsIgnoreCase(args[1]);
            if (on) {
                config.setAlertsEnabled(true);
                sender.sendMessage("§aGuard alerts enabled.");
                return true;
            }
            if (off) {
                config.setAlertsEnabled(false);
                sender.sendMessage("§aGuard alerts disabled.");
                return true;
            }
        }
        sender.sendMessage("§7Guard alerts: §f" + (config.alertsEnabled() ? "on" : "off"));
        return true;
    }

    private static String on(boolean value) {
        return value ? "on" : "off";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("yapguard.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (String opt : List.of("status", "reload", "alerts")) {
                if (opt.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(opt);
                }
            }
            return out;
        }
        if (args.length == 2 && "alerts".equalsIgnoreCase(args[0])) {
            List<String> out = new ArrayList<>();
            for (String opt : List.of("on", "off")) {
                if (opt.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(opt);
                }
            }
            return out;
        }
        if (args.length == 2 && "status".equalsIgnoreCase(args[0])) {
            List<String> out = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(player.getName());
                }
            }
            return out;
        }
        return List.of();
    }
}
