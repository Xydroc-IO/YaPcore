package com.yapcore.yap420.market;

import com.yapcore.playerdata.PlayerDataService;
import com.yapcore.playerdata.PlayerDataServiceProvider;
import org.bukkit.entity.Player;

import java.util.Optional;

/** YaPPlayerData economy soft bridge. */
public final class EconomyBridge {

    public Optional<PlayerDataService> service() {
        return PlayerDataServiceProvider.find();
    }

    public boolean available() {
        return service().map(PlayerDataService::economyEnabled).orElse(false);
    }

    public double balance(Player player) {
        return service().map(s -> s.balance(player.getUniqueId())).orElse(0.0);
    }

    public String format(double amount) {
        return service().map(s -> s.formatMoney(amount)).orElse(String.format("$%.2f", amount));
    }

    public Optional<Double> deposit(Player player, double amount) {
        if (amount <= 0) {
            return Optional.empty();
        }
        return service().flatMap(s -> {
            if (!s.economyEnabled()) {
                return Optional.empty();
            }
            return s.deposit(player.getUniqueId(), amount);
        });
    }

    public Optional<Double> withdraw(Player player, double amount) {
        if (amount <= 0) {
            return Optional.empty();
        }
        return service().flatMap(s -> {
            if (!s.economyEnabled()) {
                return Optional.empty();
            }
            return s.withdraw(player.getUniqueId(), amount);
        });
    }
}
