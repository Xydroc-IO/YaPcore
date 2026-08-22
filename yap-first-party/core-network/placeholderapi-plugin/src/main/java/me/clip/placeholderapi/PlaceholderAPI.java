package me.clip.placeholderapi;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.Relational;
import me.clip.placeholderapi.replacer.CharsReplacer;
import me.clip.placeholderapi.replacer.Replacer;
import me.clip.placeholderapi.replacer.Replacer.Closure;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Static PlaceholderAPI entry points used by plugins.
 * YaPcore clean-room — binary-compatible with {@code me.clip.placeholderapi.PlaceholderAPI}.
 */
public final class PlaceholderAPI {

    private static final Replacer REPLACER_PERCENT = new CharsReplacer(Closure.PERCENT);
    private static final Replacer REPLACER_BRACKET = new CharsReplacer(Closure.BRACKET);

    static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("[%]([^%]+)[%]");
    static final Pattern BRACKET_PLACEHOLDER_PATTERN = Pattern.compile("[{]([^{}]+)[}]");
    static final Pattern RELATIONAL_PLACEHOLDER_PATTERN = Pattern.compile("[%](rel_)([^%]+)[%]");

    private PlaceholderAPI() {
    }

    @NotNull
    public static String setPlaceholders(final OfflinePlayer player, @NotNull final String text) {
        return REPLACER_PERCENT.apply(
                text,
                player,
                PlaceholderAPIPlugin.getInstance().getLocalExpansionManager()::getExpansion);
    }

    @NotNull
    public static List<String> setPlaceholders(final OfflinePlayer player, @NotNull final List<String> text) {
        final List<String> result = new ArrayList<>(text.size());
        for (final String line : text) {
            result.add(setPlaceholders(player, line));
        }
        return result;
    }

    @NotNull
    public static String setPlaceholders(final Player player, @NotNull String text) {
        return setPlaceholders((OfflinePlayer) player, text);
    }

    @NotNull
    public static List<String> setPlaceholders(final Player player, @NotNull List<String> text) {
        return setPlaceholders((OfflinePlayer) player, text);
    }

    @NotNull
    public static String setBracketPlaceholders(final OfflinePlayer player, @NotNull final String text) {
        return REPLACER_BRACKET.apply(
                text,
                player,
                PlaceholderAPIPlugin.getInstance().getLocalExpansionManager()::getExpansion);
    }

    @NotNull
    public static List<String> setBracketPlaceholders(
            final OfflinePlayer player, @NotNull final List<String> text) {
        final List<String> result = new ArrayList<>(text.size());
        for (final String line : text) {
            result.add(setBracketPlaceholders(player, line));
        }
        return result;
    }

    @NotNull
    public static String setBracketPlaceholders(Player player, @NotNull String text) {
        return setBracketPlaceholders((OfflinePlayer) player, text);
    }

    @NotNull
    public static List<String> setBracketPlaceholders(Player player, @NotNull List<String> text) {
        return setBracketPlaceholders((OfflinePlayer) player, text);
    }

    public static String setRelationalPlaceholders(final Player one, final Player two, @NotNull String text) {
        final Matcher matcher = RELATIONAL_PLACEHOLDER_PATTERN.matcher(text);
        String result = text;

        while (matcher.find()) {
            final String format = matcher.group(2);
            final int index = format.indexOf('_');
            if (index <= 0) {
                continue;
            }

            String identifier = format.substring(0, index).toLowerCase(Locale.ROOT);
            String params = format.substring(index + 1);
            final PlaceholderExpansion expansion = PlaceholderAPIPlugin.getInstance()
                    .getLocalExpansionManager()
                    .getExpansion(identifier);

            if (!(expansion instanceof Relational relational)) {
                continue;
            }

            final String value = relational.onPlaceholderRequest(one, two, params);
            if (value != null) {
                result = result.replace(matcher.group(), value);
            }
        }
        return result;
    }

    public static List<String> setRelationalPlaceholders(
            final Player one, final Player two, @NotNull final List<String> text) {
        final List<String> result = new ArrayList<>(text.size());
        for (final String line : text) {
            result.add(setRelationalPlaceholders(one, two, line));
        }
        return result;
    }

    public static boolean isRegistered(@NotNull final String identifier) {
        return PlaceholderAPIPlugin.getInstance()
                .getLocalExpansionManager()
                .findExpansionByIdentifier(identifier)
                .isPresent();
    }

    @NotNull
    public static Set<String> getRegisteredIdentifiers() {
        return PlaceholderAPIPlugin.getInstance().getLocalExpansionManager().getIdentifiers();
    }

    public static Pattern getPlaceholderPattern() {
        return PLACEHOLDER_PATTERN;
    }

    public static Pattern getBracketPlaceholderPattern() {
        return BRACKET_PLACEHOLDER_PATTERN;
    }

    public static Pattern getRelationalPlaceholderPattern() {
        return RELATIONAL_PLACEHOLDER_PATTERN;
    }

    public static boolean containsPlaceholders(final String text) {
        if (text == null) {
            return false;
        }
        final int first = text.indexOf('%');
        if (first < 0) {
            return false;
        }
        return text.indexOf('%', first + 1) >= 0;
    }

    public static boolean containsBracketPlaceholders(final String text) {
        if (text == null) {
            return false;
        }
        final int open = text.indexOf('{');
        if (open < 0) {
            return false;
        }
        return text.indexOf('}', open + 1) >= 0;
    }

    @Deprecated
    public static boolean registerExpansion(PlaceholderExpansion expansion) {
        return expansion.register();
    }

    @Deprecated
    public static boolean unregisterExpansion(PlaceholderExpansion expansion) {
        return expansion.unregister();
    }

    @Deprecated
    public static Map<String, PlaceholderHook> getPlaceholders() {
        return PlaceholderAPIPlugin.getInstance().getLocalExpansionManager().getExpansions().stream()
                .collect(Collectors.toMap(PlaceholderExpansion::getIdentifier, ex -> ex));
    }

    @Deprecated
    public static boolean registerPlaceholderHook(Plugin plugin, PlaceholderHook placeholderHook) {
        return false;
    }

    @Deprecated
    public static boolean registerPlaceholderHook(String identifier, PlaceholderHook placeholderHook) {
        return false;
    }

    @Deprecated
    public static boolean unregisterPlaceholderHook(Plugin plugin) {
        return false;
    }

    @Deprecated
    public static boolean unregisterPlaceholderHook(String identifier) {
        return false;
    }

    @Deprecated
    public static Set<String> getRegisteredPlaceholderPlugins() {
        return getRegisteredIdentifiers();
    }

    @Deprecated
    public static Set<String> getExternalPlaceholderPlugins() {
        return null;
    }

    @Deprecated
    public static String setPlaceholders(OfflinePlayer player, String text, Pattern pattern, boolean colorize) {
        return setPlaceholders(player, text);
    }

    @Deprecated
    public static List<String> setPlaceholders(
            OfflinePlayer player, List<String> text, Pattern pattern, boolean colorize) {
        return setPlaceholders(player, text);
    }

    @Deprecated
    public static List<String> setPlaceholders(OfflinePlayer player, List<String> text, boolean colorize) {
        return setPlaceholders(player, text);
    }

    @Deprecated
    public static List<String> setPlaceholders(OfflinePlayer player, List<String> text, Pattern pattern) {
        return setPlaceholders(player, text);
    }

    @Deprecated
    public static String setPlaceholders(Player player, String text, boolean colorize) {
        return setPlaceholders(player, text);
    }

    @Deprecated
    public static List<String> setPlaceholders(Player player, List<String> text, boolean colorize) {
        return setPlaceholders(player, text);
    }

    @Deprecated
    public static String setPlaceholders(OfflinePlayer player, String text, boolean colorize) {
        return setPlaceholders(player, text);
    }

    @Deprecated
    public static String setPlaceholders(OfflinePlayer player, String text, Pattern pattern) {
        return setPlaceholders(player, text);
    }

    @Deprecated
    public static List<String> setBracketPlaceholders(
            OfflinePlayer player, List<String> text, boolean colorize) {
        return setBracketPlaceholders(player, text);
    }

    @Deprecated
    public static String setBracketPlaceholders(OfflinePlayer player, String text, boolean colorize) {
        return setBracketPlaceholders(player, text);
    }

    @Deprecated
    public static String setBracketPlaceholders(Player player, String text, boolean colorize) {
        return setBracketPlaceholders(player, text);
    }

    @Deprecated
    public static List<String> setBracketPlaceholders(Player player, List<String> text, boolean colorize) {
        return setBracketPlaceholders(player, text);
    }

    @Deprecated
    public static String setRelationalPlaceholders(Player one, Player two, String text, boolean colorize) {
        return setRelationalPlaceholders(one, two, text);
    }

    @Deprecated
    public static List<String> setRelationalPlaceholders(
            Player one, Player two, List<String> text, boolean colorize) {
        return setRelationalPlaceholders(one, two, text);
    }
}
