package com.yapcore.essentials.util;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.Optional;

/** Apply a forced join gamemode (lobby adventure, etc.). */
public final class JoinGamemode {

    private JoinGamemode() {
    }

    public static void apply(Player player, Optional<GameMode> mode) {
        if (player == null || mode == null || mode.isEmpty()) {
            return;
        }
        GameMode want = mode.get();
        if (player.getGameMode() != want) {
            player.setGameMode(want);
        }
        // Creative→hub soft-switch often leaves allowFlight on; clear for non-creative.
        if (want != GameMode.CREATIVE && want != GameMode.SPECTATOR && player.getAllowFlight()) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }
}
