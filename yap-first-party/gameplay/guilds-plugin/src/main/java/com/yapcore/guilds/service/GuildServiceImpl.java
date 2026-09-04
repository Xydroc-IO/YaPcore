package com.yapcore.guilds.service;

import com.yapcore.guilds.Guild;
import com.yapcore.guilds.GuildInvite;
import com.yapcore.guilds.GuildJoinMode;
import com.yapcore.guilds.GuildMember;
import com.yapcore.guilds.GuildRelation;
import com.yapcore.guilds.GuildService;
import com.yapcore.guilds.GuildsConfig;
import com.yapcore.guilds.chat.GuildChatState;
import com.yapcore.guilds.db.GuildRepository;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class GuildServiceImpl implements GuildService {

    private final GuildServiceSupport support;
    private final GuildMembershipOps membership;
    private final GuildEconomyHomeOps economyHome;
    private final GuildRelationChatOps relationChat;

    public GuildServiceImpl(
            JavaPlugin plugin, GuildsConfig config, GuildRepository repository, GuildChatState chatState) {
        this.support = new GuildServiceSupport(plugin, config, repository, chatState);
        this.membership = new GuildMembershipOps(support);
        this.economyHome = new GuildEconomyHomeOps(support);
        this.relationChat = new GuildRelationChatOps(support);
    }

    public GuildChatState chatState() {
        return support.chatState;
    }

    @Override
    public Optional<Guild> getGuild(long guildId) {
        return support.getGuild(guildId);
    }

    @Override
    public Optional<Guild> findByName(String name) {
        return support.findByName(name);
    }

    @Override
    public Optional<Guild> findByTag(String tag) {
        return support.findByTag(tag);
    }

    @Override
    public Optional<Guild> findByPlayer(UUID playerId) {
        return support.findByPlayer(playerId);
    }

    @Override
    public Optional<GuildMember> member(UUID playerId) {
        return support.member(playerId);
    }

    @Override
    public Collection<Guild> listGuilds() {
        return support.listGuilds();
    }

    @Override
    public List<GuildMember> listMembers(long guildId) {
        return support.listMembers(guildId);
    }

    @Override
    public List<Guild> topGuilds(int page, int pageSize) {
        return support.topGuilds(page, pageSize);
    }

    @Override
    public List<GuildInvite> listInvites(UUID playerId) {
        return support.listInvites(playerId);
    }

    @Override
    public Optional<GuildInvite> inviteFor(long guildId, UUID playerId) {
        return support.inviteFor(guildId, playerId);
    }

    @Override
    public int maxMembers(long guildId) {
        return support.maxMembers(guildId);
    }

    @Override
    public double bankCap(long guildId) {
        return support.bankCap(guildId);
    }

    @Override
    public CompletableFuture<Guild> create(String name, String tag, UUID leaderId) {
        return membership.create(name, tag, leaderId);
    }

    @Override
    public CompletableFuture<Void> disband(long guildId, UUID actorId) {
        return membership.disband(guildId, actorId);
    }

    @Override
    public CompletableFuture<Void> invite(long guildId, UUID targetId, UUID actorId) {
        return membership.invite(guildId, targetId, actorId);
    }

    @Override
    public CompletableFuture<Void> acceptInvite(long guildId, UUID playerId) {
        return membership.acceptInvite(guildId, playerId);
    }

    @Override
    public CompletableFuture<Void> denyInvite(long guildId, UUID playerId) {
        return membership.denyInvite(guildId, playerId);
    }

    @Override
    public CompletableFuture<Void> join(long guildId, UUID playerId) {
        return membership.join(guildId, playerId);
    }

    @Override
    public CompletableFuture<Void> leave(UUID playerId) {
        return membership.leave(playerId);
    }

    @Override
    public CompletableFuture<Void> kick(long guildId, UUID targetId, UUID actorId) {
        return membership.kick(guildId, targetId, actorId);
    }

    @Override
    public CompletableFuture<Void> promote(long guildId, UUID targetId, UUID actorId) {
        return membership.promote(guildId, targetId, actorId);
    }

    @Override
    public CompletableFuture<Void> demote(long guildId, UUID targetId, UUID actorId) {
        return membership.demote(guildId, targetId, actorId);
    }

    @Override
    public CompletableFuture<Void> transferLeadership(long guildId, UUID targetId, UUID actorId) {
        return membership.transferLeadership(guildId, targetId, actorId);
    }

    @Override
    public CompletableFuture<Void> setDescription(long guildId, String description, UUID actorId) {
        return economyHome.setDescription(guildId, description, actorId);
    }

    @Override
    public CompletableFuture<Void> setMotd(long guildId, String motd, UUID actorId) {
        return economyHome.setMotd(guildId, motd, actorId);
    }

    @Override
    public CompletableFuture<Void> setJoinMode(long guildId, GuildJoinMode mode, UUID actorId) {
        return economyHome.setJoinMode(guildId, mode, actorId);
    }

    @Override
    public CompletableFuture<Void> setHome(long guildId, Location location, UUID actorId) {
        return economyHome.setHome(guildId, location, actorId);
    }

    @Override
    public CompletableFuture<Void> clearHome(long guildId, UUID actorId) {
        return economyHome.clearHome(guildId, actorId);
    }

    @Override
    public CompletableFuture<Void> bankDeposit(long guildId, UUID actorId, double amount) {
        return economyHome.bankDeposit(guildId, actorId, amount);
    }

    @Override
    public CompletableFuture<Void> bankWithdraw(long guildId, UUID actorId, double amount) {
        return economyHome.bankWithdraw(guildId, actorId, amount);
    }

    @Override
    public CompletableFuture<Void> setRelation(long guildId, long otherGuildId, GuildRelation relation, UUID actorId) {
        return relationChat.setRelation(guildId, otherGuildId, relation, actorId);
    }

    @Override
    public GuildRelation relationBetween(long guildIdA, long guildIdB) {
        return support.relationBetween(guildIdA, guildIdB);
    }

    @Override
    public void addGuildXp(long guildId, UUID contributorId, long amount, String source) {
        relationChat.addGuildXp(guildId, contributorId, amount, source);
    }

    @Override
    public void sendGuildChat(Player sender, String message) {
        relationChat.sendGuildChat(sender, message);
    }

    @Override
    public void sendOfficerChat(Player sender, String message) {
        relationChat.sendOfficerChat(sender, message);
    }

    @Override
    public void sendAllyChat(Player sender, String message) {
        relationChat.sendAllyChat(sender, message);
    }

    @Override
    public Map<String, Object> dashboardSnapshot() {
        return relationChat.dashboardSnapshot();
    }

    public void adminSetLevel(String guildRef, int level, Long xp) throws SQLException {
        relationChat.adminSetLevel(guildRef, level, xp);
    }

    public void adminForceDisband(String guildRef) throws SQLException {
        relationChat.adminForceDisband(guildRef);
    }
}
