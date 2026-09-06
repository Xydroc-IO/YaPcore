package com.yapcore.chat.cmd;

import com.yapcore.chat.ChatConfig;
import com.yapcore.chat.ChatPlugin;
import com.yapcore.chat.service.IgnoreService;
import com.yapcore.chat.service.PlayerChannelService;
import com.yapcore.messages.YapMessageBundle;
import com.yapcore.messages.YapText;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    private YapMessageBundle msg() {
        return config.messages();
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
            msg().playersOnly(sender);
            return true;
        }
        if (args.length < 1) {
            String current = channels.channel(player, config.defaultChannel());
            msg().sendRaw(player, "&eChannel: &f{channel}", "channel", current);
            msg().sendRaw(player, "&7Available: &f{channels}", "channels", listChannels(player));
            msg().sendRaw(player, "&e/ch <channel> &7— switch  ·  &e!<msg> &7— one-shot local");
            return true;
        }
        String ch = args[0].toLowerCase(Locale.ROOT);
        if (!config.channels().containsKey(ch)) {
            msg().send(player, "unknown-channel", "channels", listChannels(player));
            return true;
        }
        if (!config.canUseChannel(player, ch)) {
            ChatConfig.ChannelDef def = config.channel(ch);
            String need = def.requiresPermission() ? def.permission() : "yapchat.use";
            msg().send(player, "channel-no-permission", "channel", ch);
            msg().noPermission(player, need);
            return true;
        }
        channels.setChannel(player, ch);
        msg().send(player, "channel-set", "channel", ch);
        return true;
    }

    private String listChannels(Player player) {
        List<String> names = new ArrayList<>();
        for (String id : config.channels().keySet()) {
            if (config.canUseChannel(player, id)) {
                names.add(id);
            }
        }
        names.sort(String::compareTo);
        return String.join(", ", names);
    }

    private boolean clearChat(CommandSender sender) {
        if (!sender.hasPermission("yapchat.admin")) {
            msg().noPermission(sender, "yapchat.admin");
            return true;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            for (int i = 0; i < 100; i++) {
                online.sendMessage(" ");
            }
        }
        Bukkit.broadcast(YapText.component("&cChat cleared by &f" + sender.getName()));
        return true;
    }

    private boolean ignore(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            msg().playersOnly(sender);
            return true;
        }
        if (args.length < 1) {
            msg().sendRaw(player, "&e/ignore <player>");
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            msg().send(player, "player-offline");
            return true;
        }
        if (ignore.toggle(player, target)) {
            msg().sendRaw(player, "&aIgnoring &f{player}", "player", target.getName());
        } else {
            msg().sendRaw(player, "&eNo longer ignoring &f{player}", "player", target.getName());
        }
        return true;
    }

    private boolean unignore(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            msg().playersOnly(sender);
            return true;
        }
        if (args.length < 1) {
            msg().sendRaw(player, "&e/unignore <player>");
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target != null) {
            ignore.toggle(player, target);
            if (!ignore.isIgnoring(player, target)) {
                msg().sendRaw(player, "&aUnignored &f{player}", "player", target.getName());
            }
            return true;
        }
        msg().send(player, "player-offline");
        return true;
    }

    private boolean ignoreList(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            msg().playersOnly(sender);
            return true;
        }
        if (ignore.ignored(player).isEmpty()) {
            msg().sendRaw(player, "&7You are not ignoring anyone.");
            return true;
        }
        String names = ignore.ignored(player).stream()
                .map(uuid -> {
                    Player p = Bukkit.getPlayer(uuid);
                    return p != null ? p.getName() : uuid.toString();
                })
                .collect(Collectors.joining(", "));
        msg().sendRaw(player, "&7Ignoring: &f{list}", "list", names);
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
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return config.channels().keySet().stream()
                    .filter(id -> id.startsWith(prefix))
                    .filter(id -> !(sender instanceof Player p) || config.canUseChannel(p, id))
                    .sorted()
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
