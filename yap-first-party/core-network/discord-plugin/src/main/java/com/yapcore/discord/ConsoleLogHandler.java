package com.yapcore.discord;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.EnumSet;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Lightweight JUL handler that forwards filtered console lines to the Discord console channel.
 * Posts are async via JDA {@code queue()}; rate-limited. Can be noisy — prefer a dedicated channel.
 */
public final class ConsoleLogHandler extends Handler {

    private static final int DISCORD_MSG_MAX = 1900;

    private final DiscordPlugin plugin;
    private final Object rateLock = new Object();
    private long windowStartMs;
    private int windowCount;

    public ConsoleLogHandler(DiscordPlugin plugin) {
        this.plugin = plugin;
        setLevel(java.util.logging.Level.ALL);
    }

    @Override
    public void publish(LogRecord record) {
        if (record == null || !isLoggable(record)) {
            return;
        }
        DiscordConfig config = plugin.config();
        if (config == null || !config.consoleChannelEnabled() || !config.consoleChannelOutbound()) {
            return;
        }
        String loggerName = record.getLoggerName();
        if (loggerName != null && (loggerName.startsWith("net.dv8tion")
                || loggerName.startsWith("com.yapcore.discord"))) {
            // Avoid feedback from JDA / our own Discord posts.
            return;
        }
        List<String> filter = config.consoleChannelOutboundFilter();
        if (!ConsoleChannelRules.levelAllowed(record.getLevel(), filter)) {
            return;
        }
        int limit = config.consoleChannelRateLimitPer10s();
        if (!tryAcquire(limit)) {
            return;
        }
        DiscordBotService bot = plugin.bot();
        if (bot == null || !bot.isConnected()) {
            return;
        }
        String channelId = config.consoleChannelId();
        if (channelId.isBlank()) {
            return;
        }
        TextChannel channel = bot.textChannelById(channelId);
        if (channel == null) {
            return;
        }
        String levelName = record.getLevel() == null ? "?" : record.getLevel().getName();
        if ("WARNING".equals(levelName)) {
            levelName = "WARN";
        }
        String msg = record.getMessage() == null ? "" : record.getMessage();
        if (record.getParameters() != null && record.getParameters().length > 0) {
            try {
                msg = java.text.MessageFormat.format(msg, record.getParameters());
            } catch (IllegalArgumentException ignored) {
                // keep raw message
            }
        }
        Throwable thrown = record.getThrown();
        if (thrown != null) {
            msg = msg + " — " + thrown.getClass().getSimpleName() + ": " + thrown.getMessage();
        }
        String line = "`[" + levelName + "]` " + msg;
        if (line.length() > DISCORD_MSG_MAX) {
            line = TextCommandParser.truncate(line, DISCORD_MSG_MAX);
        }
        channel.sendMessage(line)
                .setAllowedMentions(EnumSet.noneOf(net.dv8tion.jda.api.entities.Message.MentionType.class))
                .queue(null, err -> {
                    // fine only — outbound must not spam the server log (feedback loop)
                });
    }

    private boolean tryAcquire(int limitPer10s) {
        if (limitPer10s <= 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        synchronized (rateLock) {
            if (now - windowStartMs >= 10_000L) {
                windowStartMs = now;
                windowCount = 0;
            }
            if (windowCount >= limitPer10s) {
                return false;
            }
            windowCount++;
            return true;
        }
    }

    @Override
    public void flush() {
        // no-op
    }

    @Override
    public void close() {
        // no-op — detach via ConsoleChannelService
    }

    /** Attach to the Bukkit / server logger root used by Paper. */
    public static void attach(Logger logger, ConsoleLogHandler handler) {
        if (logger == null || handler == null) {
            return;
        }
        for (Handler existing : logger.getHandlers()) {
            if (existing instanceof ConsoleLogHandler) {
                logger.removeHandler(existing);
            }
        }
        logger.addHandler(handler);
    }

    public static void detach(Logger logger) {
        if (logger == null) {
            return;
        }
        for (Handler existing : logger.getHandlers()) {
            if (existing instanceof ConsoleLogHandler) {
                logger.removeHandler(existing);
            }
        }
    }
}
