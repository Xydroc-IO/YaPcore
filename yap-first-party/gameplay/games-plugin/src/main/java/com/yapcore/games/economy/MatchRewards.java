package com.yapcore.games.economy;

import com.yapcore.games.GamesConfig;
import com.yapcore.games.mode.GameModeType;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class MatchRewards {

    private final GamesConfig config;

    public MatchRewards(GamesConfig config) {
        this.config = config;
    }

    public void payWinner(UUID winnerId, GameModeType type) {
        if (!config.rewardsEnabled() || winnerId == null) {
            return;
        }
        Economy economy = economy();
        if (economy == null) {
            return;
        }
        double amount = type == GameModeType.DUEL ? config.duelWinReward() : config.ffaWinReward();
        if (amount <= 0) {
            return;
        }
        Player player = Bukkit.getPlayer(winnerId);
        if (player == null) {
            return;
        }
        economy.depositPlayer(player, amount);
        player.sendMessage("§aYou earned §f" + economy.format(amount) + " §afor winning!");
    }

    private Economy economy() {
        if (Bukkit.getPluginManager().getPlugin("YaPPlayerData") == null) {
            return null;
        }
        var reg = Bukkit.getServicesManager().getRegistration(Economy.class);
        return reg == null ? null : reg.getProvider();
    }
}
