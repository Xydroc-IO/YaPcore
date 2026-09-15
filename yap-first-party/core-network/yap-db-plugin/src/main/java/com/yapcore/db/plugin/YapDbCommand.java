package com.yapcore.db.plugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import com.yapcore.messages.YapConfigReload;
import com.yapcore.messages.YapHelp;
import com.yapcore.messages.YapMessages;

final class YapDbCommand implements CommandExecutor, TabCompleter {

    private final YapDbPlugin plugin;

    YapDbCommand(YapDbPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yapdb.admin")) {
            YapMessages.noPermission(sender, "yapdb.admin");
            return true;
        }
        if (args.length == 0) {
            YapHelp.simple(sender, "YaPDB", "/yapdb <status|probe|reload>");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> {
                sender.sendMessage("YaPDB status:");
                sender.sendMessage("  open: " + plugin.isOpen());
                sender.sendMessage("  engine: " + plugin.engine());
                String product = plugin.productLabel();
                if (product == null || product.isBlank()) {
                    product = plugin.readLiveProduct().orElse("(unknown)");
                }
                sender.sendMessage("  product: " + product);
                sender.sendMessage("  pool: " + plugin.poolName());
                sender.sendMessage("  jdbc: " + plugin.jdbcUrl());
            }
            case "probe" -> {
                String host = args.length >= 2 ? args[1] : "127.0.0.1";
                sender.sendMessage("YaPDB probe host=" + host);
                for (String line : YapDbProbe.formatPortReport(plugin.probePorts(host))) {
                    sender.sendMessage(line);
                }
                if (plugin.isOpen()) {
                    String live = plugin.readLiveProduct().orElse(plugin.productLabel());
                    sender.sendMessage("Open pool product: " + (live == null || live.isBlank() ? "(n/a)" : live));
                    sender.sendMessage("Resolved dialect: " + plugin.engine());
                } else {
                    sender.sendMessage("Pool is closed — configure JDBC then /yapdb reload");
                }
            }
            case "reload" -> {
                var result = YapConfigReload.run(() -> {
                    try {
                        plugin.reloadPool();
                    } catch (Exception e) {
                        throw new IllegalStateException(e.getMessage() == null ? "reload failed" : e.getMessage(), e);
                    }
                });
                YapConfigReload.report(sender, plugin.getLogger(), "YaPDB", result);
            }
            default -> YapHelp.simple(sender, "YaPDB", "/yapdb <status|probe|reload>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            return Stream.of("status", "probe", "reload").filter(s -> s.startsWith(p)).toList();
        }
        if (args.length == 2 && "probe".equalsIgnoreCase(args[0])) {
            return List.of("127.0.0.1", "localhost");
        }
        return List.of();
    }
}
