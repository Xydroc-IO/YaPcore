package com.yapcore.protect.cmd;

import com.yapcore.protect.ProtectConfig;
import com.yapcore.protect.listener.InspectListener;
import com.yapcore.protect.service.ProtectServiceImpl;
import com.yapcore.sched.YapSched;
import com.yapcore.messages.YapMessages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class ProtectCommands implements CommandExecutor, TabCompleter {

    private final ProtectServiceImpl service;
    private ProtectConfig config;
    private final ProtectCommandOps ops;
    private final InspectListener inspect;

    public ProtectCommands(ProtectServiceImpl service, ProtectConfig config, InspectListener inspect) {
        this.service = service;
        this.config = config;
        this.ops = new ProtectCommandOps(service, config);
        this.inspect = inspect;
    }

    public void setConfig(ProtectConfig config) {
        this.config = config;
        ops.setConfig(config);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return status(sender);
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(sender);
            case "status" -> status(sender);
            case "inspect" -> inspect(sender);
            case "lookup" -> ops.lookup(sender, args);
            case "dash-lookup" -> ops.dashLookup(sender, args);
            case "export" -> ops.export(sender, args);
            case "rollback" -> ops.rollback(sender, args);
            case "restore" -> ops.restore(sender, args);
            case "prune" -> ops.prune(sender, args);
            default -> {
                help(sender);
                yield true;
            }
        };
    }

    private boolean inspect(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            YapMessages.playersOnly(sender);
            return true;
        }
        if (!player.hasPermission("yapprotect.lookup") && !player.hasPermission("yapprotect.admin")) {
            YapMessages.noPermission(sender, "yapprotect.lookup");
            return true;
        }
        boolean on = inspect.toggle(player);
        sender.sendMessage(on
                ? "§aInspect ON §7— left/right-click a block to see history (7d)."
                : "§7Inspect OFF.");
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("yapprotect.admin")) {
            YapMessages.noPermission(sender, "yapprotect.admin");
            return true;
        }
        var plugin = Bukkit.getPluginManager().getPlugin("YaPProtect");
        if (plugin instanceof com.yapcore.protect.ProtectPlugin protect) {
            try {
                protect.reloadProtect();
                YapMessages.reloaded(sender, "YaPProtect");
            } catch (java.sql.SQLException e) {
                sender.sendMessage("§cReload failed: " + e.getMessage());
            }
        }
        return true;
    }

    private boolean status(CommandSender sender) {
        if (!sender.hasPermission("yapprotect.admin")) {
            YapMessages.noPermission(sender, "yapprotect.admin");
            return true;
        }
        service.countAll().thenAccept(count -> YapSched.global(
                Bukkit.getPluginManager().getPlugin("YaPProtect"),
                () -> sender.sendMessage("§aYaPProtect §7— logging=§f" + service.isLogging()
                        + " §7rows=§f" + count + " §7server=§f" + config.serverId()
                        + " §7max-radius=§f" + config.maxRollbackRadius())));
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage("§e/yapprotect status|reload|inspect|lookup|export|rollback|restore|prune");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("status", "reload", "inspect", "lookup", "export", "rollback", "restore", "prune"), args[0]);
        }
        if (args.length == 2 && "lookup".equalsIgnoreCase(args[0])) {
            return filter(List.of("user", "block", "radius", "time"), args[1]);
        }
        if (args.length == 2 && "export".equalsIgnoreCase(args[0])) {
            return filter(List.of("user", "time"), args[1]);
        }
        if (args.length == 2 && "rollback".equalsIgnoreCase(args[0])) {
            return filter(List.of("radius", "time", "user"), args[1]);
        }
        if (args.length == 2 && "restore".equalsIgnoreCase(args[0])) {
            return filter(List.of("time", "user"), args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.startsWith(lower)).toList();
    }
}
