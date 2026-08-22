package com.yapcore.combat.integration;

import com.yapcore.combat.CombatConfig;
import com.yapcore.games.GameServices;
import org.bukkit.entity.Player;

/** PvP allow check: minigame override → global config → claim flags. */
public final class CombatPvpGate {

    private CombatPvpGate() {
    }

    public static boolean isPlayerVsPlayerAllowed(CombatConfig config, Player attacker, Player victim) {
        if (GameServices.find().map(g -> g.allowPvp(attacker, victim)).orElse(false)) {
            return true;
        }
        if (!config.pvp()) {
            return false;
        }
        return ClaimIntegration.isPvpAllowed(attacker, victim);
    }
}
