package com.yapcore.guilds.service;

import com.yapcore.guilds.Guild;
import com.yapcore.guilds.GuildHome;
import com.yapcore.guilds.GuildJoinMode;
import com.yapcore.guilds.GuildMember;
import com.yapcore.guilds.GuildRole;
import com.yapcore.guilds.integration.EconomyIntegration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class GuildEconomyHomeOps {

    private final GuildServiceSupport s;

    GuildEconomyHomeOps(GuildServiceSupport support) {
        this.s = support;
    }

    CompletableFuture<Void> setDescription(long guildId, String description, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                GuildMember actor = s.requireMember(guildId, actorId);
                if (!actor.role().atLeast(GuildRole.OFFICER)) {
                    throw new IllegalStateException("officer only");
                }
                s.repository.updateDescription(guildId, GuildServiceSupport.trimText(description, s.config.descriptionMax()));
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    CompletableFuture<Void> setMotd(long guildId, String motd, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                GuildMember actor = s.requireMember(guildId, actorId);
                if (!actor.role().atLeast(GuildRole.OFFICER)) {
                    throw new IllegalStateException("officer only");
                }
                s.repository.updateMotd(guildId, GuildServiceSupport.trimText(motd, s.config.motdMax()));
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    CompletableFuture<Void> setJoinMode(long guildId, GuildJoinMode mode, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                GuildMember actor = s.requireMember(guildId, actorId);
                if (!actor.role().atLeast(GuildRole.LEADER)) {
                    throw new IllegalStateException("leader only");
                }
                s.repository.updateJoinMode(guildId, mode);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    CompletableFuture<Void> setHome(long guildId, Location location, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                GuildMember actor = s.requireMember(guildId, actorId);
                if (!actor.role().atLeast(GuildRole.OFFICER)) {
                    throw new IllegalStateException("officer only");
                }
                if (location.getWorld() == null) {
                    throw new IllegalStateException("invalid location");
                }
                s.repository.updateHome(guildId, new GuildHome(
                        location.getWorld().getName(),
                        location.getX(), location.getY(), location.getZ(),
                        location.getYaw(), location.getPitch()));
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    CompletableFuture<Void> clearHome(long guildId, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                GuildMember actor = s.requireMember(guildId, actorId);
                if (!actor.role().atLeast(GuildRole.OFFICER)) {
                    throw new IllegalStateException("officer only");
                }
                s.repository.updateHome(guildId, GuildHome.unset());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    CompletableFuture<Void> bankDeposit(long guildId, UUID actorId, double amount) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (!s.config.bankEnabled()) {
                    throw new IllegalStateException("bank disabled");
                }
                if (amount < s.config.bankMinDeposit()) {
                    throw new IllegalArgumentException("amount too small");
                }
                s.requireMember(guildId, actorId);
                Player player = Bukkit.getPlayer(actorId);
                if (player == null || !player.isOnline()) {
                    throw new IllegalStateException("must be online");
                }
                if (!EconomyIntegration.withdraw(player, amount)) {
                    throw new IllegalStateException("insufficient funds");
                }
                Guild guild = s.repository.get(guildId).orElseThrow();
                double cap = s.bankCap(guildId);
                double next = guild.bankBalance() + amount;
                if (next > cap) {
                    throw new IllegalStateException("bank cap reached (" + (int) cap + ")");
                }
                s.repository.updateBank(guildId, next);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    CompletableFuture<Void> bankWithdraw(long guildId, UUID actorId, double amount) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (!s.config.bankEnabled()) {
                    throw new IllegalStateException("bank disabled");
                }
                if (amount < s.config.bankMinWithdraw()) {
                    throw new IllegalArgumentException("amount too small");
                }
                GuildMember actor = s.requireMember(guildId, actorId);
                if (!actor.role().atLeast(GuildRole.OFFICER)) {
                    throw new IllegalStateException("officer only");
                }
                Player player = Bukkit.getPlayer(actorId);
                if (player == null || !player.isOnline()) {
                    throw new IllegalStateException("must be online");
                }
                Guild guild = s.repository.get(guildId).orElseThrow();
                if (guild.bankBalance() < amount) {
                    throw new IllegalStateException("insufficient guild funds");
                }
                s.repository.updateBank(guildId, guild.bankBalance() - amount);
                EconomyIntegration.deposit(player, amount);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
