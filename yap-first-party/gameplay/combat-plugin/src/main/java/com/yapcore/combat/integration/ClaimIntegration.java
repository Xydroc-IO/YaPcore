package com.yapcore.combat.integration;

import com.yapcore.playerdata.PlayerDataPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Optional claim PvP gate via YaPPlayerData ({@code RegionFlag.PVP}).
 */
public final class ClaimIntegration {

    private ClaimIntegration() {
    }

    public static boolean isPvpAllowed(Player attacker, Player victim) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("YaPPlayerData");
        if (!(plugin instanceof PlayerDataPlugin playerData) || !plugin.isEnabled()) {
            return true;
        }
        return playerData.claims().isPvpAllowed(attacker, victim);
    }

    public static boolean isMobDamageAllowed(Player victim) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("YaPPlayerData");
        if (!(plugin instanceof PlayerDataPlugin playerData) || !plugin.isEnabled()) {
            return true;
        }
        return playerData.claims().isMobDamageAllowed(victim);
    }
}
