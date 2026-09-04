package com.yapcore.discord;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class DiscordCommands implements CommandExecutor {

    private final DiscordPlugin plugin;

    public DiscordCommands(DiscordPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yapdiscord.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length >= 1 && "reload".equalsIgnoreCase(args[0])) {
            plugin.reloadDiscord();
            sender.sendMessage("§aYaPDiscord reloaded.");
            return true;
        }
        if (args.length >= 2 && "test".equalsIgnoreCase(args[0])) {
            String key = args[1].toLowerCase();
            DiscordConfig config = plugin.config();
            if ("chat".equals(key)) {
                plugin.webhooks().sendPlain(config.chatWebhook(),
                        "Test chat webhook from **" + sender.getName() + "**");
                sender.sendMessage("§7Sent chat webhook test (if configured).");
                return true;
            }
            if ("events".equals(key) || "event".equals(key)) {
                plugin.webhooks().sendEmbed(config.eventsWebhook(),
                        "Test", "Events webhook from **" + sender.getName() + "**", 0x9B59B6);
                sender.sendMessage("§7Sent events webhook test (if configured).");
                return true;
            }
            plugin.webhooks().sendEmbed(config.moderationWebhook(),
                    "Test", "Webhook from **" + sender.getName() + "**", 0x3498DB);
            sender.sendMessage("§7Sent moderation webhook test (if configured).");
            return true;
        }
        if (args.length >= 2 && "say".equalsIgnoreCase(args[0])) {
            String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            plugin.mcRelay().relay(sender.getName(), message);
            sender.sendMessage("§7Relayed to global chat.");
            return true;
        }
        sender.sendMessage("§e/yapdiscord reload|test [moderation|chat|events]|say <message>");
        return true;
    }
}
