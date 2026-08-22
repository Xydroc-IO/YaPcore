package com.yapcore.games.papi;

import com.yapcore.games.GameModeId;
import com.yapcore.games.match.MatchManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** {@code %yapgame_in_match%}, {@code %yapgame_queue_mode%}, {@code %yapgame_stats_ffa_wins%}. */
public final class GamesPlaceholders extends PlaceholderExpansion {

    private final MatchManager matches;

    public GamesPlaceholders(MatchManager matches) {
        this.matches = matches;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "yapgame";
    }

    @Override
    public @NotNull String getAuthor() {
        return "YapLabs";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return null;
        }
        String lower = params.toLowerCase(Locale.ROOT);
        if ("in_match".equals(lower) || "inmatch".equals(lower)) {
            return Boolean.toString(matches.isInMatch(player.getUniqueId()));
        }
        if ("in_active_match".equals(lower) || "active".equals(lower)) {
            return Boolean.toString(matches.isInActiveMatch(player.getUniqueId()));
        }
        if ("queue_mode".equals(lower) || "queuemode".equals(lower)) {
            return matches.queueStatus(player.getUniqueId())
                    .map(q -> q.mode().id())
                    .orElse("");
        }
        if ("queue_position".equals(lower) || "queuepos".equals(lower)) {
            return matches.queueStatus(player.getUniqueId())
                    .map(q -> Integer.toString(q.position()))
                    .orElse("0");
        }
        if ("active_matches".equals(lower)) {
            return Integer.toString(matches.activeMatches().size());
        }
        if (lower.startsWith("stats_")) {
            return statField(player, lower.substring("stats_".length()));
        }
        return null;
    }

    private String statField(OfflinePlayer player, String tail) {
        int lastUnderscore = tail.lastIndexOf('_');
        if (lastUnderscore <= 0) {
            return null;
        }
        String modeRaw = tail.substring(0, lastUnderscore);
        String field = tail.substring(lastUnderscore + 1);
        GameModeId mode = GameModeId.of(modeRaw);
        var stats = matches.loadStats(player.getUniqueId(), mode);
        if (stats.isEmpty()) {
            return "0";
        }
        var s = stats.get();
        return switch (field) {
            case "wins", "win" -> Integer.toString(s.wins());
            case "kills", "kill" -> Integer.toString(s.kills());
            case "deaths", "death" -> Integer.toString(s.deaths());
            default -> null;
        };
    }

    public void tryRegister() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        register();
    }
}
