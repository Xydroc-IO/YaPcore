package com.yapcore.discord;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

import java.awt.Color;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Optional JDA gateway bot. All Bukkit access from JDA threads is scheduled via YapSched.
 */
public final class DiscordBotService extends ListenerAdapter {

    private final DiscordPlugin plugin;
    private final DiscordBotMemberOps memberOps;
    private final DiscordBotSlashHandlers slashHandlers;
    private final DiscordBotMessageHandlers messageHandlers;
    private volatile JDA jda;
    private volatile boolean ready;

    public DiscordBotService(DiscordPlugin plugin) {
        this.plugin = plugin;
        this.memberOps = new DiscordBotMemberOps(plugin, this);
        this.slashHandlers = new DiscordBotSlashHandlers(plugin, this);
        this.messageHandlers = new DiscordBotMessageHandlers(plugin, this, slashHandlers);
    }

    /** Package access for helper classes. */
    JDA jda() {
        return jda;
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
        memberOps.fetchMemberRoleIds(discordUserId, callback);
    }

    /**
     * Sets guild nickname for a linked Discord user (needs Manage Nicknames + role hierarchy).
     * Failures are logged at fine — never breaks the link flow.
     */
    public void modifyMemberNickname(String discordUserId, String nickname) {
        memberOps.modifyMemberNickname(discordUserId, nickname);
    }

    /** Grant a Discord role to a linked member (needs Manage Roles + hierarchy). */
    public void grantRole(String discordUserId, String roleId) {
        memberOps.grantRole(discordUserId, roleId);
    }

    /** Revoke a Discord role from a linked member. */
    public void revokeRole(String discordUserId, String roleId) {
        memberOps.revokeRole(discordUserId, roleId);
    }

    /** Effective guild nickname (or username) for discord→mc sync. */
    public void fetchMemberNickname(String discordUserId, Consumer<String> callback) {
        memberOps.fetchMemberNickname(discordUserId, callback);
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
            slashHandlers.registerSlashCommands(event.getJDA(), config);
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
        slashHandlers.registerSlashCommands(jda, config);
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        messageHandlers.onMessageReceived(event);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        slashHandlers.onSlashCommandInteraction(event);
    }

    /**
     * Admin slash allowlist: any configured role, else Discord Administrator permission.
     */
    static boolean memberAllowedAdminSlash(net.dv8tion.jda.api.entities.Member member, DiscordConfig config) {
        return DiscordBotMemberOps.memberAllowedAdminSlash(member, config);
    }
}
