package com.yapcore.factions.service;

import com.yapcore.factions.Faction;
import com.yapcore.factions.FactionHome;
import com.yapcore.factions.FactionJoinMode;
import com.yapcore.factions.FactionMember;
import com.yapcore.factions.FactionRole;
import com.yapcore.factions.integration.EconomyIntegration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class FactionEconomyHomeOps {

    private final FactionServiceSupport s;

    FactionEconomyHomeOps(FactionServiceSupport support) {
        this.s = support;
    }

    CompletableFuture<Void> setDescription(long factionId, String description, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                FactionMember actor = s.requireMember(factionId, actorId);
                if (!actor.role().atLeast(FactionRole.OFFICER)) {
                    throw new IllegalStateException("officer only");
                }
                String trimmed = FactionServiceSupport.trimText(description, s.config.descriptionMax());
                s.repository.updateDescription(factionId, trimmed);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    CompletableFuture<Void> setMotd(long factionId, String motd, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                FactionMember actor = s.requireMember(factionId, actorId);
                if (!actor.role().atLeast(FactionRole.OFFICER)) {
                    throw new IllegalStateException("officer only");
                }
                String trimmed = FactionServiceSupport.trimText(motd, s.config.motdMax());
                s.repository.updateMotd(factionId, trimmed);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    CompletableFuture<Void> setJoinMode(long factionId, FactionJoinMode mode, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                FactionMember actor = s.requireMember(factionId, actorId);
                if (!actor.role().atLeast(FactionRole.LEADER)) {
                    throw new IllegalStateException("leader only");
                }
                s.repository.updateJoinMode(factionId, mode);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    CompletableFuture<Void> setHome(long factionId, Location location, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                FactionMember actor = s.requireMember(factionId, actorId);
                if (!actor.role().atLeast(FactionRole.OFFICER)) {
                    throw new IllegalStateException("officer only");
                }
                if (location.getWorld() == null) {
                    throw new IllegalStateException("invalid location");
                }
                FactionHome home = new FactionHome(
                        location.getWorld().getName(),
                        location.getX(),
                        location.getY(),
                        location.getZ(),
                        location.getYaw(),
                        location.getPitch());
                s.repository.updateHome(factionId, home);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    CompletableFuture<Void> clearHome(long factionId, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                FactionMember actor = s.requireMember(factionId, actorId);
                if (!actor.role().atLeast(FactionRole.OFFICER)) {
                    throw new IllegalStateException("officer only");
                }
                s.repository.updateHome(factionId, FactionHome.unset());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    CompletableFuture<Void> bankDeposit(long factionId, UUID actorId, double amount) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (!s.config.bankEnabled()) {
                    throw new IllegalStateException("bank disabled");
                }
                if (amount < s.config.bankMinDeposit()) {
                    throw new IllegalArgumentException("amount too small");
                }
                s.requireMember(factionId, actorId);
                Player player = Bukkit.getPlayer(actorId);
                if (player == null || !player.isOnline()) {
                    throw new IllegalStateException("must be online");
                }
                if (!EconomyIntegration.withdraw(player, amount)) {
                    throw new IllegalStateException("insufficient funds");
                }
                Faction faction = s.repository.get(factionId).orElseThrow();
                s.repository.updateBank(factionId, faction.bankBalance() + amount);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    CompletableFuture<Void> bankWithdraw(long factionId, UUID actorId, double amount) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (!s.config.bankEnabled()) {
                    throw new IllegalStateException("bank disabled");
                }
                if (amount < s.config.bankMinWithdraw()) {
                    throw new IllegalArgumentException("amount too small");
                }
                FactionMember actor = s.requireMember(factionId, actorId);
                if (!actor.role().atLeast(FactionRole.OFFICER)) {
                    throw new IllegalStateException("officer only");
                }
                Player player = Bukkit.getPlayer(actorId);
                if (player == null || !player.isOnline()) {
                    throw new IllegalStateException("must be online");
                }
                Faction faction = s.repository.get(factionId).orElseThrow();
                if (faction.bankBalance() < amount) {
                    throw new IllegalStateException("insufficient faction funds");
                }
                s.repository.updateBank(factionId, faction.bankBalance() - amount);
                EconomyIntegration.deposit(player, amount);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
