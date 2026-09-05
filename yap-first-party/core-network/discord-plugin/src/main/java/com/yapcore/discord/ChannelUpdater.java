package com.yapcore.discord;

import com.yapcore.sched.YapSched;
import com.yapcore.sched.YapTask;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.Bukkit;

import java.util.Locale;

/**
 * Periodically edits a Discord channel topic from a template.
 * Bot needs {@link Permission#MANAGE_CHANNEL} on the target channel.
 */
public final class ChannelUpdater {

    private final DiscordPlugin plugin;
    private YapTask task;

    public ChannelUpdater(DiscordPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void reload(DiscordConfig config) {
        cancel();
        if (config == null || !config.channelUpdaterEnabled()) {
            return;
        }
        String channelId = config.channelUpdaterChannelId();
        if (channelId.isBlank()) {
            plugin.getLogger().warning("channel-updater.enabled but channel-id is blank — updater idle.");
            return;
        }
        int intervalSec = Math.max(15, config.channelUpdaterIntervalSeconds());
        long ticks = intervalSec * 20L;
        task = YapSched.globalTimer(plugin, this::tick, ticks, ticks);
        plugin.getLogger().info("Channel updater every " + intervalSec + "s → channel " + channelId
                + " (bot needs Manage Channels)");
    }

    public synchronized void shutdown() {
        cancel();
    }

    private void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        DiscordConfig config = plugin.config();
        DiscordBotService bot = plugin.bot();
        if (config == null || !config.channelUpdaterEnabled() || bot == null || !bot.isConnected()) {
            return;
        }
        String channelId = config.channelUpdaterChannelId();
        if (channelId.isBlank()) {
            return;
        }
        int online = Bukkit.getOnlinePlayers().size();
        String mspt = formatMspt();
        String topic = TextCommandParser.applyTopicTemplate(
                config.channelUpdaterTopicTemplate(), online, mspt, Bukkit.getMaxPlayers());
        if (topic.length() > 1024) {
            topic = topic.substring(0, 1024);
        }
        final String topicFinal = topic;
        TextChannel channel = bot.textChannelById(channelId);
        if (channel == null) {
            plugin.getLogger().fine("channel-updater: channel not found " + channelId);
            return;
        }
        if (!channel.getGuild().getSelfMember().hasPermission(channel, Permission.MANAGE_CHANNEL)) {
            plugin.getLogger().warning("channel-updater: missing MANAGE_CHANNEL on " + channelId);
            return;
        }
        channel.getManager().setTopic(topicFinal).queue(
                ok -> plugin.getLogger().fine("channel-updater topic set"),
                err -> plugin.getLogger().fine("channel-updater failed: " + err.getMessage()));
    }

    private static String formatMspt() {
        try {
            double mspt = Bukkit.getServer().getAverageTickTime() / 1_000_000.0;
            return String.format(Locale.ROOT, "%.2f", mspt);
        } catch (Throwable t) {
            return "n/a";
        }
    }
}
