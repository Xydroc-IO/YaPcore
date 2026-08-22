package com.yapcore.guilds;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface GuildService {

    Optional<Guild> getGuild(long guildId);

    Optional<Guild> findByName(String name);

    Optional<Guild> findByTag(String tag);

    Optional<Guild> findByPlayer(UUID playerId);

    Optional<GuildMember> member(UUID playerId);

    Collection<Guild> listGuilds();

    List<GuildMember> listMembers(long guildId);

    List<Guild> topGuilds(int page, int pageSize);

    List<GuildInvite> listInvites(UUID playerId);

    Optional<GuildInvite> inviteFor(long guildId, UUID playerId);

    int maxMembers(long guildId);

    double bankCap(long guildId);

    CompletableFuture<Guild> create(String name, String tag, UUID leaderId);

    CompletableFuture<Void> disband(long guildId, UUID actorId);

    CompletableFuture<Void> invite(long guildId, UUID targetId, UUID actorId);

    CompletableFuture<Void> acceptInvite(long guildId, UUID playerId);

    CompletableFuture<Void> denyInvite(long guildId, UUID playerId);

    CompletableFuture<Void> join(long guildId, UUID playerId);

    CompletableFuture<Void> leave(UUID playerId);

    CompletableFuture<Void> kick(long guildId, UUID targetId, UUID actorId);

    CompletableFuture<Void> promote(long guildId, UUID targetId, UUID actorId);

    CompletableFuture<Void> demote(long guildId, UUID targetId, UUID actorId);

    CompletableFuture<Void> transferLeadership(long guildId, UUID targetId, UUID actorId);

    CompletableFuture<Void> setDescription(long guildId, String description, UUID actorId);

    CompletableFuture<Void> setMotd(long guildId, String motd, UUID actorId);

    CompletableFuture<Void> setJoinMode(long guildId, GuildJoinMode mode, UUID actorId);

    CompletableFuture<Void> setHome(long guildId, Location location, UUID actorId);

    CompletableFuture<Void> clearHome(long guildId, UUID actorId);

    CompletableFuture<Void> bankDeposit(long guildId, UUID actorId, double amount);

    CompletableFuture<Void> bankWithdraw(long guildId, UUID actorId, double amount);

    CompletableFuture<Void> setRelation(long guildId, long otherGuildId, GuildRelation relation, UUID actorId);

    GuildRelation relationBetween(long guildIdA, long guildIdB);

    void addGuildXp(long guildId, UUID contributorId, long amount, String source);

    void sendGuildChat(Player sender, String message);

    void sendOfficerChat(Player sender, String message);

    void sendAllyChat(Player sender, String message);

    Map<String, Object> dashboardSnapshot();
}
