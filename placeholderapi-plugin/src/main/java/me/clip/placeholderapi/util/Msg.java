package me.clip.placeholderapi.util;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.stream.Collectors;
import me.clip.placeholderapi.PlaceholderAPIPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * Logging / colored message helpers expansions and commands poke.
 * Clean-room clip-compatible surface.
 */
public final class Msg {

    private Msg() {
    }

    public static void log(Level level, String msg, Object... args) {
        PlaceholderAPIPlugin.getInstance().getLogger().log(level, format(msg, args));
    }

    public static void info(String msg, Object... args) {
        log(Level.INFO, msg, args);
    }

    public static void warn(String msg, Object... args) {
        log(Level.WARNING, msg, args);
    }

    public static void warn(String msg, Throwable throwable, Object... args) {
        PlaceholderAPIPlugin.getInstance().getLogger().log(Level.WARNING, format(msg, args), throwable);
    }

    public static void severe(String msg, Object... args) {
        log(Level.SEVERE, msg, args);
    }

    public static void severe(String msg, Throwable throwable, Object... args) {
        PlaceholderAPIPlugin.getInstance().getLogger().log(Level.SEVERE, format(msg, args), throwable);
    }

    public static void msg(@NotNull final CommandSender sender, @NotNull final String... messages) {
        if (messages.length == 0) {
            return;
        }
        sender.sendMessage(Arrays.stream(messages).map(Msg::color).collect(Collectors.joining("\n")));
    }

    public static void broadcast(@NotNull final String... messages) {
        if (messages.length == 0) {
            return;
        }
        Bukkit.broadcastMessage(Arrays.stream(messages).map(Msg::color).collect(Collectors.joining("\n")));
    }

    public static String color(@NotNull final String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private static String format(String msg, Object... args) {
        if (args == null || args.length == 0) {
            return msg;
        }
        try {
            return String.format(msg, args);
        } catch (Exception e) {
            return msg;
        }
    }
}
