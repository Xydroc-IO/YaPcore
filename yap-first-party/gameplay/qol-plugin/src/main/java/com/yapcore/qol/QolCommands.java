package com.yapcore.qol;

import com.yapcore.messages.YapMessages;
import com.yapcore.qol.gui.QolAdminGui;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Staff-only: /yapqol reload|status|give|gui
 * Players receive tools from staff — no player size GUI/command.
 */
public final class QolCommands implements CommandExecutor, TabCompleter {

    private final QolPlugin plugin;
    private final QolConfig config;
    private final QolItems items;
    private final QolAdminGui adminGui;

    public QolCommands(QolPlugin plugin, QolConfig config, QolItems items, QolAdminGui adminGui) {
        this.plugin = plugin;
        this.config = config;
        this.items = items;
        this.adminGui = adminGui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || "status".equalsIgnoreCase(args[0])) {
            if (!isStaff(sender)) {
                YapMessages.noPermission(sender, "yapqol.admin");
                return true;
            }
            sender.sendMessage("YaP-QoL enabled=" + config.enabled()
                    + " timber=" + config.timberEnabled()
                    + " excavator=" + config.excavatorEnabled()
                    + " sizes=" + config.excavatorSizes()
                    + " default=" + config.defaultSize());
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(sender);
            case "give" -> give(sender, args);
            case "gui", "admin" -> gui(sender);
            default -> {
                sender.sendMessage(
                        "Usage: /yapqol <reload|status|give <timber_axe|excavator|excavator:3|6|9> [player]|gui>");
                yield true;
            }
        };
    }

    private static boolean isStaff(CommandSender sender) {
        return sender.hasPermission("yapqol.admin")
                || sender.hasPermission("yapqol.give")
                || sender.hasPermission("yapqol.gui");
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("yapqol.admin")) {
            YapMessages.noPermission(sender, "yapqol.admin");
            return true;
        }
        plugin.reloadQol();
        YapMessages.reloaded(sender, "YaP-QoL");
        return true;
    }

    private boolean give(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapqol.give") && !sender.hasPermission("yapqol.admin")) {
            YapMessages.noPermission(sender, "yapqol.give");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("Usage: /yapqol give <timber_axe|excavator|excavator:3|excavator:6|excavator:9> [player]");
            return true;
        }
        String tool = args[1];
        Player target;
        if (args.length >= 3 && looksLikeSize(args[2]) && tool.equalsIgnoreCase("excavator")) {
            tool = "excavator:" + args[2];
            target = args.length >= 4
                    ? Bukkit.getPlayerExact(args[3])
                    : (sender instanceof Player p ? p : null);
        } else {
            target = args.length >= 3
                    ? Bukkit.getPlayerExact(args[2])
                    : (sender instanceof Player p ? p : null);
        }
        if (target == null) {
            sender.sendMessage("Player not found.");
            return true;
        }
        try {
            boolean fitted = items.give(target, tool);
            if (fitted) {
                sender.sendMessage("Gave " + tool + " to " + target.getName());
            } else {
                sender.sendMessage("Gave " + tool + " to " + target.getName()
                        + " (inventory full — dropped at their feet)");
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage("Unknown tool. Use timber_axe, excavator, or excavator:3|6|9");
        } catch (RuntimeException e) {
            sender.sendMessage("Could not create " + tool + ": " + e.getMessage());
            plugin.getLogger().warning("yapqol give failed for " + tool + ": " + e.getMessage());
        }
        return true;
    }

    private static boolean looksLikeSize(String raw) {
        try {
            int n = Integer.parseInt(raw);
            return n >= 1 && n <= 15;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean gui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            YapMessages.playersOnly(sender);
            return true;
        }
        if (!isStaff(player)) {
            YapMessages.noPermission(player, "yapqol.gui");
            return true;
        }
        adminGui.open(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!isStaff(sender)) {
            return List.of();
        }
        if (args.length == 1) {
            return Stream.of("reload", "status", "give", "gui")
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && "give".equalsIgnoreCase(args[0])) {
            List<String> tools = new ArrayList<>();
            tools.add("timber_axe");
            tools.add("excavator");
            for (int s : config.excavatorSizes()) {
                tools.add("excavator:" + s);
            }
            return tools.stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 3 && "give".equalsIgnoreCase(args[0])) {
            return null;
        }
        return List.of();
    }
}
