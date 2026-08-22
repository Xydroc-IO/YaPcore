package me.clip.placeholderapi;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base hook for placeholder expansions (clip PlaceholderAPI-compatible surface).
 * YaPcore clean-room — not GPL PlaceholderAPI source.
 */
public abstract class PlaceholderHook {

    @Nullable
    public String onRequest(final OfflinePlayer player, @NotNull final String params) {
        if (player != null && player.isOnline()) {
            return onPlaceholderRequest(player.getPlayer(), params);
        }
        return onPlaceholderRequest(null, params);
    }

    @Nullable
    public String onPlaceholderRequest(final Player player, @NotNull final String params) {
        return null;
    }
}
