package com.yapcore.games;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Optional;

public final class GameServices {

    private GameServices() {
    }

    public static Optional<GameService> find() {
        var reg = Bukkit.getServicesManager().getRegistration(GameService.class);
        return reg == null ? Optional.empty() : Optional.of(reg.getProvider());
    }

    public static boolean allowPvp(Player attacker, Player victim) {
        return find()
                .map(g -> g.allowPvp(attacker, victim))
                .orElse(false);
    }

    public static boolean suppressesSkillXp(java.util.UUID playerId) {
        return find()
                .map(g -> g.suppressesSkillXp(playerId))
                .orElse(false);
    }
}
