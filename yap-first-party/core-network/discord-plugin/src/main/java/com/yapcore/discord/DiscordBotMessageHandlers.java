package com.yapcore.discord;

import com.yapcore.sched.YapSched;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Inbound message / text-command handling for {@link DiscordBotService}. */
final class DiscordBotMessageHandlers {

    private static final int DISCORD_MSG_MAX = 1900;

    private final DiscordPlugin plugin;
    private final DiscordBotSlashHandlers slash;

    DiscordBotMessageHandlers(DiscordPlugin plugin, DiscordBotService bot, DiscordBotSlashHandlers slash) {
        this.plugin = plugin;
        this.slash = slash;
    }

    void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.isWebhookMessage()) {
            return;
        }
        DiscordConfig config = plugin.config();
        if (config == null || !config.botEnabled()) {
            return;
        }
        // Console channel (not chat): inbound commands before any relay / text triggers.
        ConsoleChannelService console = plugin.consoleChannel();
        if (console != null && event.isFromGuild() && console.tryHandleInbound(event, config)) {
            return;
        }
        // DM link flow: message body is the short code.
        if (!event.isFromGuild() && config.linkEnabled()) {
            String raw = event.getMessage().getContentRaw().trim();
            if (DiscordBotSlashHandlers.looksLikeLinkCode(raw)) {
                slash.handleLinkCode(raw, event.getAuthor().getId(), reply ->
                        event.getChannel().sendMessage(reply).queue());
                return;
            }
        }
        if (event.isFromGuild() && config.textCommandsEnabled()) {
            if (shouldHandleTextCommands(event, config)) {
                String raw = event.getMessage().getContentRaw();
                TextCommandParser.Parsed parsed = TextCommandParser.parse(
                        raw, config.textCommandsPlayerlist(), config.textCommandsConsolePrefix());
                if (parsed.kind() == TextCommandParser.Kind.CONSOLE
                        && (config.textCommandsRequireRoles().isEmpty()
                        || config.textCommandsConsolePrefix().isBlank())) {
                    // !c disabled — do not consume; may still relay as chat
                    parsed = TextCommandParser.Parsed.none();
                }
                if (parsed.kind() != TextCommandParser.Kind.NONE) {
                    handleTextCommand(event, config, parsed);
                    return;
                }
            }
        }
        if (!config.discordToMc()) {
            return;
        }
        String channelId = config.botChatChannelId();
        if (channelId.isBlank() || !channelId.equals(event.getChannel().getId())) {
            return;
        }
        if (!config.botGuildId().isBlank()
                && event.isFromGuild()
                && !config.botGuildId().equals(event.getGuild().getId())) {
            return;
        }
        String content = config.filterInboundContent(event.getMessage().getContentDisplay());
        if (content.isBlank()) {
            return;
        }
        String author = event.getAuthor().getName();
        // Never touch Bukkit from the JDA thread.
        YapSched.global(plugin, () -> plugin.mcRelay().relay(author, content));
    }

    private boolean shouldHandleTextCommands(MessageReceivedEvent event, DiscordConfig config) {
        if (!config.botGuildId().isBlank() && !config.botGuildId().equals(event.getGuild().getId())) {
            return false;
        }
        if (config.textCommandsListenAllGuild()) {
            return true;
        }
        String channelId = config.botChatChannelId();
        return !channelId.isBlank() && channelId.equals(event.getChannel().getId());
    }

    private void handleTextCommand(MessageReceivedEvent event, DiscordConfig config,
                                   TextCommandParser.Parsed parsed) {
        if (parsed.kind() == TextCommandParser.Kind.PLAYERLIST) {
            YapSched.global(plugin, () -> {
                String list = DiscordBotSlashHandlers.onlinePlayerList();
                event.getChannel().sendMessage("**Players:** " + list)
                        .setAllowedMentions(EnumSet.noneOf(net.dv8tion.jda.api.entities.Message.MentionType.class))
                        .queue();
            });
            return;
        }
        if (parsed.kind() == TextCommandParser.Kind.CONSOLE) {
            List<String> requireRoles = config.textCommandsRequireRoles();
            if (requireRoles.isEmpty() || config.textCommandsConsolePrefix().isBlank()) {
                return; // !c disabled when roles empty or prefix blank
            }
            Member member = event.getMember();
            Set<String> roleIds = DiscordBotMemberOps.memberRoleIds(member);
            if (!TextCommandParser.hasAnyRole(roleIds, requireRoles)) {
                event.getChannel().sendMessage("No permission for console commands.")
                        .queue();
                return;
            }
            if (!TextCommandParser.isConsoleCommandAllowed(
                    parsed.consoleCommand(), config.textCommandsConsoleWhitelist())) {
                event.getChannel().sendMessage("Command not in whitelist.")
                        .queue();
                return;
            }
            String cmd = parsed.consoleCommand();
            YapSched.global(plugin, () -> {
                String out = ConsoleCapture.dispatchCapturing(cmd, DISCORD_MSG_MAX);
                event.getChannel().sendMessage(out)
                        .setAllowedMentions(EnumSet.noneOf(net.dv8tion.jda.api.entities.Message.MentionType.class))
                        .queue();
            });
        }
    }
}
