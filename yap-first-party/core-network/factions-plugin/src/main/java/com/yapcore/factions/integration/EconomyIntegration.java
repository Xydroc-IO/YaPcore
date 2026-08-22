package com.yapcore.factions.integration;

import com.yapcore.playerdata.PlayerDataPlugin;
import com.yapcore.playerdata.sync.SyncService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.UUID;

/** Soft bridge to YaPPlayerdata economy. */
public final class EconomyIntegration {

    private EconomyIntegration() {
    }

    public static Optional<SyncService> sync() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("YaPPlayerdata");
        if (!(plugin instanceof PlayerDataPlugin playerData) || !plugin.isEnabled()) {
            return Optional.empty();
        }
        return Optional.of(playerData.sync());
    }

    public static boolean withdraw(Player player, double amount) {
        Optional<SyncService> sync = sync();
        if (sync.isEmpty()) {
            return false;
        }
        UUID id = player.getUniqueId();
        if (sync.get().getBalance(id) < amount) {
            return false;
        }
        sync.get().setBalanceLocal(id, sync.get().getBalance(id) - amount);
        return true;
    }

    public static boolean deposit(Player player, double amount) {
        Optional<SyncService> sync = sync();
        if (sync.isEmpty()) {
            return false;
        }
        UUID id = player.getUniqueId();
        sync.get().setBalanceLocal(id, sync.get().getBalance(id) + amount);
        return true;
    }

    public static double balance(Player player) {
        return sync().map(s -> s.getBalance(player.getUniqueId())).orElse(0.0);
    }
}
