package com.yapcore.discord;

import com.yapcore.sched.YapSched;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.bukkit.Bukkit;

import java.util.EnumSet;

/**
 * DiscordSRV-style console channel: Discord → console commands, Minecraft logs → Discord.
 * Folia-safe: inbound dispatch runs on {@link YapSched#global}.
 */
public final class ConsoleChannelService {

    private static final int DISCORD_MSG_MAX = 1900;

    private final DiscordPlugin plugin;
    private volatile ConsoleLogHandler logHandler;

    public ConsoleChannelService(DiscordPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void reload(DiscordConfig config) {
        detachOutbound();
        if (config == null || !config.consoleChannelEnabled()) {
            return;
        }
        if (config.consoleChannelId().isBlank()) {
            plugin.getLogger().warning("console-channel.enabled but channel-id is blank — idle.");
            return;
        }
        if (config.consoleChannelOutbound()) {
            ConsoleLogHandler handler = new ConsoleLogHandler(plugin);
            // Bukkit/Paper server logger only — avoid attaching to root JUL (double-fire via parent).
            ConsoleLogHandler.attach(Bukkit.getLogger(), handler);
            logHandler = handler;
            plugin.getLogger().info("Console channel outbound attached (can be noisy; rate-limited).");
        }
        if (config.consoleChannelInbound()) {
            plugin.getLogger().info("Console channel inbound enabled → channel "
                    + config.consoleChannelId());
        }
    }

    public synchronized void shutdown() {
        detachOutbound();
    }

    private void detachOutbound() {
        ConsoleLogHandler.detach(Bukkit.getLogger());
        logHandler = null;
    }

    /**
     * @return true if the message was consumed as a console-channel command (do not chat-relay)
     */
    public boolean tryHandleInbound(MessageReceivedEvent event, DiscordConfig config) {
        if (config == null || !config.consoleChannelEnabled() || !config.consoleChannelInbound()) {
            return false;
        }
        String channelId = config.consoleChannelId();
        if (channelId.isBlank() || !channelId.equals(event.getChannel().getId())) {
            return false;
        }
        if (!config.botGuildId().isBlank()
                && event.isFromGuild()
                && !config.botGuildId().equals(event.getGuild().getId())) {
            return false;
        }
        // Never treat console channel as chat relay / text-commands.
        String raw = event.getMessage().getContentRaw();
        if (raw == null || raw.isBlank()) {
            return true;
        }
        String cmd = raw.trim();
        if (cmd.startsWith("/")) {
            cmd = cmd.substring(1).trim();
        }
        if (cmd.isBlank()) {
            return true;
        }
        Member member = event.getMember();
        if (!ConsoleChannelRules.memberAllowedInbound(member, config.consoleChannelInboundRoles())) {
            event.getChannel().sendMessage("No permission for console channel.")
                    .queue();
            return true;
        }
        if (!ConsoleChannelRules.isCommandAllowed(cmd, config.consoleChannelCommandWhitelist())) {
            event.getChannel().sendMessage("Command not in whitelist.")
                    .queue();
            return true;
        }
        final String command = cmd;
        YapSched.global(plugin, () -> {
            String out = ConsoleCapture.dispatchCapturing(command, DISCORD_MSG_MAX);
            event.getChannel().sendMessage(out)
                    .setAllowedMentions(EnumSet.noneOf(net.dv8tion.jda.api.entities.Message.MentionType.class))
                    .queue();
        });
        return true;
    }
}
