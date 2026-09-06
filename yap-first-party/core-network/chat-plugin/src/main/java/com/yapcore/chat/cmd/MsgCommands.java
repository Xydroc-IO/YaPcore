package com.yapcore.chat.cmd;

import com.yapcore.chat.ChatConfig;
import com.yapcore.chat.ChatFormat;
import com.yapcore.chat.ChatPlugin;
import com.yapcore.chat.service.PlayerChannelService;
import com.yapcore.chat.service.PrivateMessageService;
import com.yapcore.messages.YapMessageBundle;
import com.yapcore.messages.YapText;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class MsgCommands implements CommandExecutor, TabCompleter {

    private final ChatPlugin plugin;
    private final ChatConfig config;
    private final PrivateMessageService pm;
    private final PlayerChannelService channels;

    public MsgCommands(ChatPlugin plugin, ChatConfig config, PrivateMessageService pm,
                       PlayerChannelService channels) {
        this.plugin = plugin;
        this.config = config;
        this.pm = pm;
        this.channels = channels;
    }

    private YapMessageBundle msg() {
        return config.messages();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if ("yapchat".equals(name)) {
            if (!sender.hasPermission("yapchat.admin")) {
                msg().noPermission(sender, "yapchat.admin");
                return true;
            }
            if (args.length >= 1 && "reload".equalsIgnoreCase(args[0])) {
                plugin.reloadChat();
                msg().reloaded(sender, "YaPChat");
                return true;
            }
            msg().sendRaw(sender, "&e/yapchat reload");
            return true;
        }
        if ("staffchat".equals(name) || "sc".equals(name)) {
            return quickChannel(sender, args, "staff", config.staffFormat(), "yapchat.staff");
        }
        if ("adminchat".equals(name) || "ac".equals(name)) {
            return quickChannel(sender, args, "admin", config.adminFormat(), "yapchat.admin");
        }
        if ("reply".equals(name) || "r".equals(name)) {
            return reply(sender, args);
        }
        return msgCmd(sender, args);
    }

    private boolean quickChannel(CommandSender sender, String[] args, String channelId,
                                 String oneShotFormat, String fallbackPerm) {
        ChatConfig.ChannelDef def = config.channel(channelId);
        String need = def.requiresPermission() ? def.permission() : fallbackPerm;
        if (!sender.hasPermission(need) && !config.canUseChannel(sender, channelId)) {
            msg().noPermission(sender, need);
            return true;
        }
        if (args.length < 1) {
            if (!(sender instanceof Player player)) {
                msg().sendRaw(sender, "&e/" + channelId + "chat <message>");
                return true;
            }
            String current = channels.channel(player, config.defaultChannel());
            if (channelId.equals(current)) {
                channels.setChannel(player, config.defaultChannel());
                msg().sendRaw(player, "&eLeft &f{channel} &echannel → &f{default}",
                        "channel", channelId, "default", config.defaultChannel());
            } else {
                channels.setChannel(player, channelId);
                msg().sendRaw(player, "&aJoined &f{channel} &achannel. &7Type again with no args to leave.",
                        "channel", channelId);
            }
            return true;
        }
        String message = join(args, 0);
        String line = YapText.apply(oneShotFormat,
                Map.of("player", sender.getName(), "prefix", "", "suffix", "", "message", message));
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (config.canUseChannel(online, channelId)) {
                ChatFormat.sendSystem(online, YapText.component(line));
            }
        }
        return true;
    }

    private boolean msgCmd(CommandSender sender, String[] args) {
        if (!(sender instanceof Player from)) {
            msg().playersOnly(sender);
            return true;
        }
        if (!from.hasPermission("yapchat.msg")) {
            msg().noPermission(from, "yapchat.msg");
            return true;
        }
        if (args.length < 2) {
            msg().sendRaw(from, "&e/msg <player> <message>");
            return true;
        }
        Player to = Bukkit.getPlayer(args[0]);
        if (to == null) {
            msg().send(from, "player-offline");
            return true;
        }
        deliverPrivate(from, to, join(args, 1));
        return true;
    }

    private boolean reply(CommandSender sender, String[] args) {
        if (!(sender instanceof Player from)) {
            msg().playersOnly(sender);
            return true;
        }
        if (!from.hasPermission("yapchat.msg")) {
            msg().noPermission(from, "yapchat.msg");
            return true;
        }
        if (args.length < 1) {
            msg().sendRaw(from, "&e/reply <message>");
            return true;
        }
        Player to = pm.replyTarget(from);
        if (to == null) {
            msg().send(from, "no-reply-target");
            return true;
        }
        deliverPrivate(from, to, join(args, 0));
        return true;
    }

    private void deliverPrivate(Player from, Player to, String message) {
        pm.sent(from, to);
        msg().sendRaw(from, config.pmSent(), "target", to.getName(), "message", message);
        msg().sendRaw(to, config.pmReceived(), "sender", from.getName(), "message", message);
        for (Player spy : Bukkit.getOnlinePlayers()) {
            if (spy.equals(from) || spy.equals(to) || !spy.hasPermission("yapchat.socialspy")) {
                continue;
            }
            msg().sendRaw(spy, config.socialSpyFormat(),
                    "sender", from.getName(), "target", to.getName(), "message", message);
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
