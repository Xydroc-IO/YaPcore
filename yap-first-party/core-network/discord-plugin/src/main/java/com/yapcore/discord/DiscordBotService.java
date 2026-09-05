package com.yapcore.discord;

import com.yapcore.discord.link.DiscordLink;
import com.yapcore.discord.link.DiscordLinkService;
import com.yapcore.discord.slash.DiscordSlashRegistrarImpl;
import com.yapcore.sched.YapSched;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Optional JDA gateway bot. All Bukkit access from JDA threads is scheduled via YapSched.
 */
public final class DiscordBotService extends ListenerAdapter {

    private static final int DISCORD_MSG_MAX = 1900;

    private final DiscordPlugin plugin;
    private volatile JDA jda;
    private volatile boolean ready;

    public DiscordBotService(DiscordPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void reload(DiscordConfig config) {
        shutdown();
        if (config == null || !config.botEnabled()) {
            return;
        }
        String token = config.botToken();
        if (token.isBlank()) {
            plugin.getLogger().warning("Discord bot.enabled but bot.token is blank — bot not started.");
            return;
        }
        try {
            jda = JDABuilder.createDefault(token)
                    .enableIntents(
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT,
                            GatewayIntent.DIRECT_MESSAGES,
                            GatewayIntent.GUILD_MEMBERS)
                    .setMemberCachePolicy(MemberCachePolicy.NONE)
                    .setActivity(Activity.watching("Minecraft"))
                    .addEventListeners(this)
                    .build();
            plugin.getLogger().info("Discord bot connecting…");
        } catch (Exception e) {
            plugin.getLogger().warning("Discord bot failed to start: " + e.getMessage());
            jda = null;
        }
    }

    public synchronized void shutdown() {
        ready = false;
        JDA local = jda;
        jda = null;
        if (local != null) {
            try {
                local.shutdownNow();
            } catch (Exception e) {
                plugin.getLogger().fine("bot shutdown: " + e.getMessage());
            }
        }
    }

    public boolean isConnected() {
        return ready && jda != null && jda.getStatus() == JDA.Status.CONNECTED;
    }

    public String statusLine() {
        DiscordConfig config = plugin.config();
        if (config == null || !config.botEnabled()) {
            return "bot disabled";
        }
        if (config.botToken().isBlank()) {
            return "bot enabled, token missing";
        }
        if (!isConnected()) {
            return "connecting / not ready (guild=" + config.botGuildId()
                    + " channel=" + config.botChatChannelId() + ")";
        }
        return "connected guild=" + config.botGuildId()
                + " channel=" + config.botChatChannelId();
    }

    /** Prefer bot chat channel when connected; otherwise no-op (caller may use webhook). */
    public boolean sendPlain(String content) {
        if (!isConnected() || content == null || content.isBlank()) {
            return false;
        }
        TextChannel channel = chatChannel();
        if (channel == null) {
            return false;
        }
        channel.sendMessage(content)
                .setAllowedMentions(EnumSet.noneOf(net.dv8tion.jda.api.entities.Message.MentionType.class))
                .queue(null, err -> plugin.getLogger().warning("bot chat send failed: " + err.getMessage()));
        return true;
    }

    public boolean sendEmbed(String title, String description, int color) {
        if (!isConnected()) {
            return false;
        }
        TextChannel channel = chatChannel();
        if (channel == null) {
            return false;
        }
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(title == null ? "" : title)
                .setDescription(description == null ? "" : description)
                .setColor(new Color(color & 0xFFFFFF));
        channel.sendMessageEmbeds(embed.build())
                .queue(null, err -> plugin.getLogger().warning("bot embed send failed: " + err.getMessage()));
        return true;
    }

    /**
     * Fetches Discord member role snowflakes for role sync. Runs on JDA threads; callback may be async.
     */
    public void fetchMemberRoleIds(String discordUserId, Consumer<Set<String>> callback) {
        if (!isConnected() || jda == null || discordUserId == null || discordUserId.isBlank()) {
            callback.accept(Set.of());
            return;
        }
        DiscordConfig config = plugin.config();
        String guildId = config == null ? "" : config.botGuildId();
        if (guildId.isBlank()) {
            callback.accept(Set.of());
            return;
        }
        var guild = jda.getGuildById(guildId);
        if (guild == null) {
            callback.accept(Set.of());
            return;
        }
        guild.retrieveMemberById(discordUserId).queue(
                member -> {
                    Set<String> ids = member.getRoles().stream()
                            .map(Role::getId)
                            .collect(Collectors.toUnmodifiableSet());
                    callback.accept(ids);
                },
                err -> {
                    plugin.getLogger().fine("member role fetch failed: " + err.getMessage());
                    callback.accept(Set.of());
                });
    }

    /**
     * Sets guild nickname for a linked Discord user (needs Manage Nicknames + role hierarchy).
     * Failures are logged at fine — never breaks the link flow.
     */
    public void modifyMemberNickname(String discordUserId, String nickname) {
        if (!isConnected() || jda == null || discordUserId == null || discordUserId.isBlank()) {
            return;
        }
        if (nickname == null || nickname.isBlank()) {
            return;
        }
        String nick = nickname.length() > 32 ? nickname.substring(0, 32) : nickname;
        DiscordConfig config = plugin.config();
        String guildId = config == null ? "" : config.botGuildId();
        if (guildId.isBlank()) {
            return;
        }
        var guild = jda.getGuildById(guildId);
        if (guild == null) {
            return;
        }
        guild.retrieveMemberById(discordUserId).queue(
                member -> member.modifyNickname(nick).queue(
                        ok -> plugin.getLogger().fine("nickname sync mc→discord: " + nick),
                        err -> plugin.getLogger().fine("nickname sync failed: " + err.getMessage())),
                err -> plugin.getLogger().fine("nickname member fetch failed: " + err.getMessage()));
    }

    /** Effective guild nickname (or username) for discord→mc sync. */
    public void fetchMemberNickname(String discordUserId, Consumer<String> callback) {
        if (!isConnected() || jda == null || discordUserId == null || discordUserId.isBlank()) {
            callback.accept(null);
            return;
        }
        DiscordConfig config = plugin.config();
        String guildId = config == null ? "" : config.botGuildId();
        if (guildId.isBlank()) {
            callback.accept(null);
            return;
        }
        var guild = jda.getGuildById(guildId);
        if (guild == null) {
            callback.accept(null);
            return;
        }
        guild.retrieveMemberById(discordUserId).queue(
                member -> {
                    String nick = member.getNickname();
                    if (nick == null || nick.isBlank()) {
                        nick = member.getUser().getName();
                    }
                    callback.accept(nick);
                },
                err -> {
                    plugin.getLogger().fine("nickname fetch failed: " + err.getMessage());
                    callback.accept(null);
                });
    }

    public TextChannel textChannelById(String channelId) {
        if (!isConnected() || jda == null || channelId == null || channelId.isBlank()) {
            return null;
        }
        try {
            return jda.getTextChannelById(channelId);
        } catch (Exception e) {
            return null;
        }
    }

    private TextChannel chatChannel() {
        DiscordConfig config = plugin.config();
        if (config == null) {
            return null;
        }
        return textChannelById(config.botChatChannelId());
    }

    @Override
    public void onReady(ReadyEvent event) {
        ready = true;
        DiscordConfig config = plugin.config();
        plugin.getLogger().info("Discord bot ready as " + event.getJDA().getSelfUser().getName());
        if (config != null && config.botSlashCommands()) {
            registerSlashCommands(event.getJDA(), config);
        }
    }

    /** Re-push slash commands when extension providers change (bot must be ready). */
    public void refreshSlashCommands() {
        if (!isConnected() || jda == null) {
            return;
        }
        DiscordConfig config = plugin.config();
        if (config == null || !config.botSlashCommands()) {
            return;
        }
        registerSlashCommands(jda, config);
    }

    private List<CommandData> builtinSlashCommands(DiscordConfig config) {
        List<CommandData> cmds = new ArrayList<>();
        cmds.add(Commands.slash("status", "Server online count and tick stats"));
        cmds.add(Commands.slash("players", "List online player names"));
        if (config.linkEnabled()) {
            cmds.add(Commands.slash("link", "Link your Minecraft account with an in-game code")
                    .addOption(OptionType.STRING, "code", "Code from /discord link in-game", true));
            cmds.add(Commands.slash("unlink", "Unlink your Minecraft account (self)"));
            cmds.add(Commands.slash("linked", "Show your linked Minecraft account"));
            cmds.add(Commands.slash("resync", "Re-apply Discord roles → YaPPerms for your linked account"));
        }
        cmds.add(Commands.slash("broadcast", "Broadcast a message to the Minecraft chat channel mirror")
                .addOption(OptionType.STRING, "message", "Message to send", true));
        return cmds;
    }

    private void registerSlashCommands(JDA jda, DiscordConfig config) {
        List<CommandData> builtins = builtinSlashCommands(config);
        DiscordSlashRegistrarImpl registrar = plugin.slashRegistrar();
        List<CommandData> cmds = registrar == null
                ? builtins
                : registrar.mergeWithBuiltins(builtins);
        String guildId = config.botGuildId();
        if (!guildId.isBlank()) {
            var guild = jda.getGuildById(guildId);
            if (guild != null) {
                guild.updateCommands().addCommands(cmds).queue(
                        ok -> plugin.getLogger().info("Registered guild slash commands (" + cmds.size() + ")"),
                        err -> plugin.getLogger().warning("Slash command register failed: " + err.getMessage()));
                return;
            }
            plugin.getLogger().warning("bot.guild-id not found yet — registering global slash commands");
        }
        jda.updateCommands().addCommands(cmds).queue(
                ok -> plugin.getLogger().info("Registered global slash commands (" + cmds.size() + ")"),
                err -> plugin.getLogger().warning("Slash command register failed: " + err.getMessage()));
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
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
            if (looksLikeLinkCode(raw)) {
                handleLinkCode(raw, event.getAuthor().getId(), reply ->
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
                String list = onlinePlayerList();
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
            Set<String> roleIds = memberRoleIds(member);
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

    private static Set<String> memberRoleIds(Member member) {
        if (member == null) {
            return Set.of();
        }
        Set<String> ids = new HashSet<>();
        for (Role role : member.getRoles()) {
            ids.add(role.getId());
        }
        return ids;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        DiscordConfig config = plugin.config();
        if (config == null || !config.botSlashCommands()) {
            event.reply("Slash commands disabled.").setEphemeral(true).queue();
            return;
        }
        String name = event.getName();
        switch (name) {
            case "link" -> handleSlashLink(event, config);
            case "unlink" -> handleSlashUnlink(event, config);
            case "linked" -> handleSlashLinked(event, config);
            case "resync" -> handleSlashResync(event, config);
            case "broadcast" -> handleSlashBroadcast(event, config);
            case "status" -> {
                event.deferReply(true).queue();
                YapSched.global(plugin, () -> {
                    int online = Bukkit.getOnlinePlayers().size();
                    int max = Bukkit.getMaxPlayers();
                    String ticks = tickSummary();
                    event.getHook().sendMessage("**Online:** " + online + "/" + max + "\n" + ticks).queue();
                });
            }
            case "players" -> {
                event.deferReply(true).queue();
                YapSched.global(plugin, () ->
                        event.getHook().sendMessage("**Players:** " + onlinePlayerList()).queue());
            }
            default -> {
                DiscordSlashRegistrarImpl registrar = plugin.slashRegistrar();
                if (registrar != null && registrar.dispatch(event)) {
                    return;
                }
                event.reply("Unknown command.").setEphemeral(true).queue();
            }
        }
    }

    private void handleSlashLink(SlashCommandInteractionEvent event, DiscordConfig config) {
        if (!config.linkEnabled()) {
            event.reply("Account linking is disabled.").setEphemeral(true).queue();
            return;
        }
        var opt = event.getOption("code");
        String code = opt == null ? "" : opt.getAsString();
        event.deferReply(true).queue();
        handleLinkCode(code, event.getUser().getId(), reply ->
                event.getHook().sendMessage(reply).queue());
    }

    private void handleSlashUnlink(SlashCommandInteractionEvent event, DiscordConfig config) {
        if (!config.linkEnabled()) {
            event.reply("Account linking is disabled.").setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue();
        DiscordLinkService links = plugin.linkService();
        if (links == null || !links.isReady()) {
            event.getHook().sendMessage("Account linking is not available on this server.").queue();
            return;
        }
        String discordId = event.getUser().getId();
        YapSched.async(plugin, () -> {
            DiscordLinkService.UnlinkResult result = links.unlinkByDiscordId(discordId);
            event.getHook().sendMessage(result.success()
                    ? "Unlinked your Minecraft account."
                    : result.message()).queue();
        });
    }

    private void handleSlashLinked(SlashCommandInteractionEvent event, DiscordConfig config) {
        if (!config.linkEnabled()) {
            event.reply("Account linking is disabled.").setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue();
        DiscordLinkService links = plugin.linkService();
        if (links == null || !links.isReady()) {
            event.getHook().sendMessage("Account linking is not available on this server.").queue();
            return;
        }
        String discordId = event.getUser().getId();
        YapSched.async(plugin, () -> {
            Optional<DiscordLink> link = links.findByDiscordId(discordId);
            if (link.isEmpty()) {
                event.getHook().sendMessage("Not linked. Run `/discord link` in-game, then `/link <code>` here.")
                        .queue();
                return;
            }
            DiscordLink l = link.get();
            UUID uuid = l.mcUuid();
            YapSched.global(plugin, () -> {
                String mcName = DiscordLinkService.resolvePlayerName(uuid);
                String namePart = mcName == null ? "(unknown name)" : mcName;
                event.getHook().sendMessage("**Linked**\nMinecraft: **" + namePart + "**\nUUID: `"
                        + uuid + "`\nVerified: " + l.verified()).queue();
            });
        });
    }

    private void handleSlashResync(SlashCommandInteractionEvent event, DiscordConfig config) {
        if (!config.linkEnabled()) {
            event.reply("Account linking is disabled.").setEphemeral(true).queue();
            return;
        }
        if (!config.roleSyncEnabled()) {
            event.reply("Role sync is disabled.").setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue();
        DiscordLinkService links = plugin.linkService();
        if (links == null || !links.isReady()) {
            event.getHook().sendMessage("Account linking is not available on this server.").queue();
            return;
        }
        String discordId = event.getUser().getId();
        YapSched.async(plugin, () -> {
            Optional<DiscordLink> link = links.findByDiscordId(discordId);
            if (link.isEmpty()) {
                event.getHook().sendMessage("Not linked — nothing to resync.").queue();
                return;
            }
            YapSched.global(plugin, () -> {
                links.resyncRoles(link.get());
                event.getHook().sendMessage("Role resync queued for your linked account.").queue();
            });
        });
    }

    private void handleSlashBroadcast(SlashCommandInteractionEvent event, DiscordConfig config) {
        if (!memberAllowedAdminSlash(event.getMember(), config)) {
            event.reply("No permission (needs a role in `bot.slash-roles.admin` or Administrator).")
                    .setEphemeral(true).queue();
            return;
        }
        var opt = event.getOption("message");
        String message = opt == null ? "" : opt.getAsString().trim();
        if (message.isBlank()) {
            event.reply("Message required.").setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue();
        String line = "**[Discord Broadcast]** " + message;
        // Prefer bot channel; webhook fallback via plugin.relayMcChat
        boolean sent = sendPlain(line);
        if (!sent) {
            plugin.relayMcChat(line);
        }
        event.getHook().sendMessage("Broadcast sent.").queue();
    }

    /**
     * Admin slash allowlist: any configured role, else Discord Administrator permission.
     */
    static boolean memberAllowedAdminSlash(Member member, DiscordConfig config) {
        if (member == null || config == null) {
            return false;
        }
        List<String> allowed = config.slashRolesAdmin();
        if (allowed != null && !allowed.isEmpty()) {
            return TextCommandParser.hasAnyRole(memberRoleIds(member), allowed);
        }
        return member.hasPermission(Permission.ADMINISTRATOR);
    }

    private void handleLinkCode(String code, String discordId, Consumer<String> reply) {
        DiscordLinkService links = plugin.linkService();
        if (links == null || !links.isReady()) {
            reply.accept("Account linking is not available on this server.");
            return;
        }
        // SQL off the JDA thread; Bukkit notify/role sync inside completeLink uses YapSched.
        YapSched.async(plugin, () -> {
            DiscordLinkService.LinkResult result = links.completeLink(code, discordId);
            reply.accept(result.message());
        });
    }

    private static boolean looksLikeLinkCode(String raw) {
        if (raw == null) {
            return false;
        }
        String n = DiscordLinkService.normalizeCode(raw);
        return n.length() >= 4 && n.length() <= 12;
    }

    private static String onlinePlayerList() {
        String list = Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.joining(", "));
        return list.isBlank() ? "(none)" : list;
    }

    private static String tickSummary() {
        try {
            double mspt = Bukkit.getServer().getAverageTickTime() / 1_000_000.0;
            double[] tps = Bukkit.getTPS();
            String tps1 = tps != null && tps.length > 0 ? String.format("%.2f", Math.min(20.0, tps[0])) : "n/a";
            return String.format("**MSPT:** %.2f  **TPS:** %s", mspt, tps1);
        } catch (Throwable t) {
            return "**MSPT/TPS:** unavailable";
        }
    }
}
