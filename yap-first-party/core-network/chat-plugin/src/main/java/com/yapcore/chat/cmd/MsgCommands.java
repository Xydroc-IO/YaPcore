package com.yapcore.chat.cmd;

import com.yapcore.chat.ChatConfig;
import com.yapcore.chat.ChatFormat;
import com.yapcore.chat.ChatPlugin;
import com.yapcore.chat.service.PrivateMessageService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class MsgCommands implements CommandExecutor, TabCompleter {

    private final ChatPlugin plugin;
    private final ChatConfig config;
    private final PrivateMessageService pm;

    public MsgCommands(ChatPlugin plugin, ChatConfig config, PrivateMessageService pm) {
        this.plugin = plugin;
        this.config = config;
        this.pm = pm;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if ("yapchat".equals(name)) {
            if (!sender.hasPermission("yapchat.admin")) {
                sender.sendMessage(ChatFormat.legacy("&cNo permission."));
                return true;
            }
            if (args.length >= 1 && "reload".equalsIgnoreCase(args[0])) {
                plugin.reloadChat();
                sender.sendMessage(ChatFormat.legacy("&aYaPChat reloaded."));
                return true;
            }
            sender.sendMessage(ChatFormat.legacy("&e/yapchat reload"));
            return true;
        }
        if ("staffchat".equals(name) || "sc".equals(name) || "ac".equals(name)) {
            return staffChat(sender, args);
        }
        if ("reply".equals(name) || "r".equals(name)) {
            return reply(sender, args);
        }
        return msg(sender, args);
    }

    private boolean msg(CommandSender sender, String[] args) {
        if (!(sender instanceof Player from)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!from.hasPermission("yapchat.msg")) {
            from.sendMessage(ChatFormat.legacy("&cNo permission."));
            return true;
        }
        if (args.length < 2) {
            from.sendMessage(ChatFormat.legacy("&e/msg <player> <message>"));
            return true;
        }
        Player to = Bukkit.getPlayer(args[0]);
        if (to == null) {
            from.sendMessage(ChatFormat.legacy("&cPlayer not online."));
            return true;
        }
        String message = join(args, 1);
        deliverPrivate(from, to, message);
        return true;
    }

    private boolean reply(CommandSender sender, String[] args) {
        if (!(sender instanceof Player from)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!from.hasPermission("yapchat.msg")) {
            from.sendMessage(ChatFormat.legacy("&cNo permission."));
            return true;
        }
        if (args.length < 1) {
            from.sendMessage(ChatFormat.legacy("&e/reply <message>"));
            return true;
        }
        Player to = pm.replyTarget(from);
        if (to == null) {
            from.sendMessage(ChatFormat.legacy("&cNo one to reply to."));
            return true;
        }
        deliverPrivate(from, to, join(args, 0));
        return true;
    }

    private boolean staffChat(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapchat.staff")) {
            sender.sendMessage(ChatFormat.legacy("&cNo permission."));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ChatFormat.legacy("&e/staffchat <message>"));
            return true;
        }
        String message = join(args, 0);
        String line = ChatFormat.color(config.staffFormat()
                .replace("{player}", sender.getName())
                .replace("{message}", message));
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("yapchat.staff")) {
                online.sendMessage(ChatFormat.legacy(line));
            }
        }
        return true;
    }

    private void deliverPrivate(Player from, Player to, String message) {
        pm.sent(from, to);
        from.sendMessage(ChatFormat.legacy(ChatFormat.color(config.pmSent()
                .replace("{target}", to.getName())
                .replace("{message}", message))));
        to.sendMessage(ChatFormat.legacy(ChatFormat.color(config.pmReceived()
                .replace("{sender}", from.getName())
                .replace("{message}", message))));
        for (Player spy : Bukkit.getOnlinePlayers()) {
            if (spy.equals(from) || spy.equals(to) || !spy.hasPermission("yapchat.socialspy")) {
                continue;
            }
            spy.sendMessage(ChatFormat.legacy(ChatFormat.color(config.socialSpyFormat()
                    .replace("{sender}", from.getName())
                    .replace("{target}", to.getName())
                    .replace("{message}", message))));
        }
    }

    private static String join(String[] args, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (i > start) {
                sb.append(' ');
            }
            sb.append(args[i]);
        }
        return sb.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && ("msg".equalsIgnoreCase(command.getName())
                || "m".equalsIgnoreCase(alias))) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if ("yapchat".equalsIgnoreCase(command.getName()) && args.length == 1) {
            return List.of("reload");
        }
        return List.of();
    }
}
