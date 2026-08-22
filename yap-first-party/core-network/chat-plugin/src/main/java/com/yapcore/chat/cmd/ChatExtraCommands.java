package com.yapcore.chat.cmd;

import com.yapcore.chat.ChatConfig;
import com.yapcore.chat.ChatFormat;
import com.yapcore.chat.ChatPlugin;
import com.yapcore.chat.service.IgnoreService;
import com.yapcore.chat.service.PlayerChannelService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ChatExtraCommands implements CommandExecutor, TabCompleter {

    private final ChatPlugin plugin;
    private final ChatConfig config;
    private final PlayerChannelService channels;
    private final IgnoreService ignore;

    public ChatExtraCommands(ChatPlugin plugin, ChatConfig config,
                               PlayerChannelService channels, IgnoreService ignore) {
        this.plugin = plugin;
        this.config = config;
        this.channels = channels;
        this.ignore = ignore;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        return switch (name) {
            case "channel", "ch" -> channel(sender, args);
            case "clearchat", "cc" -> clearChat(sender);
            case "ignore" -> ignore(sender, args);
            case "unignore" -> unignore(sender, args);
            case "ignorelist", "ignored" -> ignoreList(sender);
            default -> false;
        };
    }

    private boolean channel(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(ChatFormat.legacy("&eChannel: &f" + channels.channel(player, config.defaultChannel())));
            player.sendMessage(ChatFormat.legacy("&e/ch <global|local|staff>"));
            return true;
        }
        String ch = args[0].toLowerCase(Locale.ROOT);
        if ("staff".equals(ch) && !player.hasPermission("yapchat.staff")) {
            player.sendMessage(ChatFormat.legacy("&cNo permission."));
            return true;
        }
        if (!config.channels().containsKey(ch) && !"staff".equals(ch)) {
            player.sendMessage(ChatFormat.legacy("&cUnknown channel."));
            return true;
        }
        channels.setChannel(player, ch);
        player.sendMessage(ChatFormat.legacy("&aChat channel set to &f" + ch));
        return true;
    }

    private boolean clearChat(CommandSender sender) {
        if (!sender.hasPermission("yapchat.admin")) {
            sender.sendMessage(ChatFormat.legacy("&cNo permission."));
            return true;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            for (int i = 0; i < 100; i++) {
                online.sendMessage(" ");
            }
        }
        Bukkit.broadcastMessage(ChatFormat.color("&cChat cleared by &f" + sender.getName()));
        return true;
    }

    private boolean ignore(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(ChatFormat.legacy("&e/ignore <player>"));
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage(ChatFormat.legacy("&cPlayer not online."));
            return true;
        }
        if (ignore.toggle(player, target)) {
            player.sendMessage(ChatFormat.legacy("&aIgnoring &f" + target.getName()));
        } else {
            player.sendMessage(ChatFormat.legacy("&eNo longer ignoring &f" + target.getName()));
        }
        return true;
    }

    private boolean unignore(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(ChatFormat.legacy("&e/unignore <player>"));
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target != null) {
            ignore.toggle(player, target);
            if (!ignore.isIgnoring(player, target)) {
                player.sendMessage(ChatFormat.legacy("&aUnignored &f" + target.getName()));
            }
            return true;
        }
        player.sendMessage(ChatFormat.legacy("&cPlayer must be online."));
        return true;
    }

    private boolean ignoreList(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (ignore.ignored(player).isEmpty()) {
            player.sendMessage(ChatFormat.legacy("&7You are not ignoring anyone."));
            return true;
        }
        String names = ignore.ignored(player).stream()
                .map(uuid -> {
                    Player p = Bukkit.getPlayer(uuid);
                    return p != null ? p.getName() : uuid.toString();
                })
                .collect(Collectors.joining(", "));
        player.sendMessage(ChatFormat.legacy("&7Ignoring: &f" + names));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && ("ignore".equalsIgnoreCase(command.getName())
                || "unignore".equalsIgnoreCase(command.getName()))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if (args.length == 1 && ("channel".equalsIgnoreCase(command.getName()) || "ch".equalsIgnoreCase(command.getName()))) {
            return List.of("global", "local", "staff");
        }
        return List.of();
    }
}
