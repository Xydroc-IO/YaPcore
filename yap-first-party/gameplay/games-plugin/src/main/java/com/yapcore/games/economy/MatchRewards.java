package com.yapcore.games.economy;

import com.yapcore.games.GamesConfig;
import com.yapcore.games.mode.GameModeType;
import com.yapcore.playerdata.PlayerDataService;
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
        PlayerDataService economy = economy();
        if (economy == null || !economy.economyEnabled()) {
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
        if (economy.deposit(winnerId, amount).isEmpty()) {
            return;
        }
        player.sendMessage("§aYou earned §f" + economy.formatMoney(amount) + " §afor winning!");
    }

    private PlayerDataService economy() {
        if (Bukkit.getPluginManager().getPlugin("YaPPlayerData") == null) {
            return null;
        }
        return Bukkit.getServicesManager().load(PlayerDataService.class);
    }
}
