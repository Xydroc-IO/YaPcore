package com.yapcore.factions.service;

import com.yapcore.factions.Faction;
import com.yapcore.factions.FactionClaimOverlay;
import com.yapcore.factions.FactionInvite;
import com.yapcore.factions.FactionJoinMode;
import com.yapcore.factions.FactionMember;
import com.yapcore.factions.FactionRelation;
import com.yapcore.factions.FactionService;
import com.yapcore.factions.FactionsConfig;
import com.yapcore.factions.chat.FactionChatState;
import com.yapcore.factions.db.FactionRepository;
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

public final class FactionServiceImpl implements FactionService {

    private final FactionServiceSupport support;
    private final FactionMembershipOps membership;
    private final FactionEconomyHomeOps economyHome;
    private final FactionRelationChatOps relationChat;
    private final FactionClaimGuardOps claimGuard;

    public FactionServiceImpl(
            JavaPlugin plugin,
            FactionsConfig config,
            FactionRepository repository,
            FactionChatState chatState) {
        this.support = new FactionServiceSupport(plugin, config, repository, chatState);
        this.membership = new FactionMembershipOps(support);
        this.economyHome = new FactionEconomyHomeOps(support);
        this.relationChat = new FactionRelationChatOps(support);
        this.claimGuard = new FactionClaimGuardOps(support);
    }

    public FactionChatState chatState() {
        return support.chatState;
    }

    @Override
    public Optional<Faction> getFaction(long factionId) {
        return support.getFaction(factionId);
    }

    @Override
    public Optional<Faction> findByName(String name) {
        return support.findByName(name);
    }

    @Override
    public Optional<Faction> findByTag(String tag) {
        return support.findByTag(tag);
    }

    @Override
    public Optional<Faction> findByPlayer(UUID playerId) {
        return support.findByPlayer(playerId);
    }

    @Override
    public Optional<FactionMember> member(UUID playerId) {
        return support.member(playerId);
    }

    @Override
    public Optional<FactionClaimOverlay> overlayForClaim(long claimId) {
        return support.overlayForClaim(claimId);
    }

    @Override
    public Collection<Faction> listFactions() {
        return support.listFactions();
    }

    @Override
    public List<FactionMember> listMembers(long factionId) {
        return support.listMembers(factionId);
    }

    @Override
    public List<FactionClaimOverlay> listClaims(long factionId) {
        return support.listClaims(factionId);
    }

    @Override
    public List<Faction> topFactions(int page, int pageSize) {
        return support.topFactions(page, pageSize);
    }

    @Override
    public List<FactionInvite> listInvites(UUID playerId) {
        return support.listInvites(playerId);
    }

    @Override
    public Optional<FactionInvite> inviteFor(long factionId, UUID playerId) {
        return support.inviteFor(factionId, playerId);
    }

    @Override
    public CompletableFuture<Faction> create(String name, String tag, UUID leaderId) {
        return membership.create(name, tag, leaderId);
    }

    @Override
    public CompletableFuture<Void> disband(long factionId, UUID actorId) {
        return membership.disband(factionId, actorId);
    }

    @Override
    public CompletableFuture<Void> invite(long factionId, UUID targetId, UUID actorId) {
        return membership.invite(factionId, targetId, actorId);
    }

    @Override
    public CompletableFuture<Void> acceptInvite(long factionId, UUID playerId) {
        return membership.acceptInvite(factionId, playerId);
    }

    @Override
    public CompletableFuture<Void> denyInvite(long factionId, UUID playerId) {
        return membership.denyInvite(factionId, playerId);
    }

    @Override
    public CompletableFuture<Void> join(long factionId, UUID playerId) {
        return membership.join(factionId, playerId);
    }

    @Override
    public CompletableFuture<Void> leave(UUID playerId) {
        return membership.leave(playerId);
    }

    @Override
    public CompletableFuture<Void> kick(long factionId, UUID targetId, UUID actorId) {
        return membership.kick(factionId, targetId, actorId);
    }

    @Override
    public CompletableFuture<Void> promote(long factionId, UUID targetId, UUID actorId) {
        return membership.promote(factionId, targetId, actorId);
    }

    @Override
    public CompletableFuture<Void> demote(long factionId, UUID targetId, UUID actorId) {
        return membership.demote(factionId, targetId, actorId);
    }

    @Override
    public CompletableFuture<Void> transferLeadership(long factionId, UUID targetId, UUID actorId) {
        return membership.transferLeadership(factionId, targetId, actorId);
    }

    @Override
    public CompletableFuture<Void> setDescription(long factionId, String description, UUID actorId) {
        return economyHome.setDescription(factionId, description, actorId);
    }

    @Override
    public CompletableFuture<Void> setMotd(long factionId, String motd, UUID actorId) {
        return economyHome.setMotd(factionId, motd, actorId);
    }

    @Override
    public CompletableFuture<Void> setJoinMode(long factionId, FactionJoinMode mode, UUID actorId) {
        return economyHome.setJoinMode(factionId, mode, actorId);
    }

    @Override
    public CompletableFuture<Void> setHome(long factionId, Location location, UUID actorId) {
        return economyHome.setHome(factionId, location, actorId);
    }

    @Override
    public CompletableFuture<Void> clearHome(long factionId, UUID actorId) {
        return economyHome.clearHome(factionId, actorId);
    }

    @Override
    public CompletableFuture<Void> bankDeposit(long factionId, UUID actorId, double amount) {
        return economyHome.bankDeposit(factionId, actorId, amount);
    }

    @Override
    public CompletableFuture<Void> bankWithdraw(long factionId, UUID actorId, double amount) {
        return economyHome.bankWithdraw(factionId, actorId, amount);
    }

    @Override
    public CompletableFuture<Void> setRelation(
            long factionId, long otherFactionId, FactionRelation relation, UUID actorId) {
        return relationChat.setRelation(factionId, otherFactionId, relation, actorId);
    }

    @Override
    public FactionRelation relationBetween(long factionIdA, long factionIdB) {
        return support.relationBetween(factionIdA, factionIdB);
    }

    @Override
    public CompletableFuture<FactionClaimOverlay> linkClaim(
            long claimId, long factionId, UUID actorId, int claimArea) {
        return claimGuard.linkClaim(claimId, factionId, actorId, claimArea);
    }

    @Override
    public CompletableFuture<Integer> linkAllClaims(
            long factionId, UUID actorId, List<Long> claimIds, List<Integer> claimAreas) {
        return claimGuard.linkAllClaims(factionId, actorId, claimIds, claimAreas);
    }

    @Override
    public CompletableFuture<Void> unlinkClaim(long claimId, UUID actorId) {
        return claimGuard.unlinkClaim(claimId, actorId);
    }

    @Override
    public int claimPowerCost(int claimArea) {
        return support.claimPowerCost(claimArea);
    }

    @Override
    public int maxPowerForMembers(int memberCount) {
        return support.maxPowerForMembers(memberCount);
    }

    @Override
    public Optional<Long> factionAt(Location location) {
        return claimGuard.factionAt(location);
    }

    @Override
    public void sendFactionChat(Player sender, String message) {
        relationChat.sendFactionChat(sender, message);
    }

    @Override
    public void sendAllyChat(Player sender, String message) {
        relationChat.sendAllyChat(sender, message);
    }

    @Override
    public Optional<Boolean> evaluateBuild(Player player, long claimId, UUID claimOwnerId) {
        return claimGuard.evaluateBuild(player, claimId, claimOwnerId);
    }

    @Override
    public Optional<Boolean> evaluatePvp(Player attacker, Player victim, long claimId) {
        return claimGuard.evaluatePvp(attacker, victim, claimId);
    }

    @Override
    public Map<String, Object> dashboardSnapshot() {
        return claimGuard.dashboardSnapshot();
    }

    public void invalidateOverlay(long claimId) {
        claimGuard.invalidateOverlay(claimId);
    }

    public void applyDeathPowerLoss(UUID playerId) {
        claimGuard.applyDeathPowerLoss(playerId);
    }

    public void regenPowerTick() {
        claimGuard.regenPowerTick();
    }

    public void adminSetPower(String factionRef, int power, Integer maxPower) throws SQLException {
        claimGuard.adminSetPower(factionRef, power, maxPower);
    }

    public void adminSetJoinMode(String factionRef, FactionJoinMode mode) throws SQLException {
        claimGuard.adminSetJoinMode(factionRef, mode);
    }

    public void adminForceDisband(String factionRef) throws SQLException {
        claimGuard.adminForceDisband(factionRef);
    }
}
