package com.yapcore.factions;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface FactionService {

    Optional<Faction> getFaction(long factionId);

    Optional<Faction> findByName(String name);

    Optional<Faction> findByTag(String tag);

    Optional<Faction> findByPlayer(UUID playerId);

    Optional<FactionMember> member(UUID playerId);

    Optional<FactionClaimOverlay> overlayForClaim(long claimId);

    Collection<Faction> listFactions();

    List<FactionMember> listMembers(long factionId);

    List<FactionClaimOverlay> listClaims(long factionId);

    List<Faction> topFactions(int page, int pageSize);

    List<FactionInvite> listInvites(UUID playerId);

    Optional<FactionInvite> inviteFor(long factionId, UUID playerId);

    CompletableFuture<Faction> create(String name, String tag, UUID leaderId);

    CompletableFuture<Void> disband(long factionId, UUID actorId);

    CompletableFuture<Void> invite(long factionId, UUID targetId, UUID actorId);

    CompletableFuture<Void> acceptInvite(long factionId, UUID playerId);

    CompletableFuture<Void> denyInvite(long factionId, UUID playerId);

    CompletableFuture<Void> join(long factionId, UUID playerId);

    CompletableFuture<Void> leave(UUID playerId);

    CompletableFuture<Void> kick(long factionId, UUID targetId, UUID actorId);

    CompletableFuture<Void> promote(long factionId, UUID targetId, UUID actorId);

    CompletableFuture<Void> demote(long factionId, UUID targetId, UUID actorId);

    CompletableFuture<Void> transferLeadership(long factionId, UUID targetId, UUID actorId);

    CompletableFuture<Void> setDescription(long factionId, String description, UUID actorId);

    CompletableFuture<Void> setMotd(long factionId, String motd, UUID actorId);

    CompletableFuture<Void> setJoinMode(long factionId, FactionJoinMode mode, UUID actorId);

    CompletableFuture<Void> setHome(long factionId, Location location, UUID actorId);

    CompletableFuture<Void> clearHome(long factionId, UUID actorId);

    CompletableFuture<Void> bankDeposit(long factionId, UUID actorId, double amount);

    CompletableFuture<Void> bankWithdraw(long factionId, UUID actorId, double amount);

    CompletableFuture<Void> setRelation(long factionId, long otherFactionId, FactionRelation relation, UUID actorId);

    FactionRelation relationBetween(long factionIdA, long factionIdB);

    CompletableFuture<FactionClaimOverlay> linkClaim(long claimId, long factionId, UUID actorId, int claimArea);

    CompletableFuture<Integer> linkAllClaims(long factionId, UUID actorId, List<Long> claimIds, List<Integer> claimAreas);

    CompletableFuture<Void> unlinkClaim(long claimId, UUID actorId);

    int claimPowerCost(int claimArea);

    int maxPowerForMembers(int memberCount);

    Optional<Long> factionAt(Location location);

    void sendFactionChat(Player sender, String message);

    void sendAllyChat(Player sender, String message);

    /**
     * When a claim has a faction overlay, returns whether the player may build.
     * Empty when the claim is not faction-linked (caller should use normal claim rules).
     */
    Optional<Boolean> evaluateBuild(Player player, long claimId, UUID claimOwnerId);

    /**
     * When both players are in faction context on this claim, returns whether PvP is allowed.
     * Empty when factions do not override claim PvP for this pair.
     */
    Optional<Boolean> evaluatePvp(Player attacker, Player victim, long claimId);

    Map<String, Object> dashboardSnapshot();
}
