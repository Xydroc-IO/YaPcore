package com.yapcore.factions.service;

import com.yapcore.factions.Faction;
import com.yapcore.factions.FactionClaimOverlay;
import com.yapcore.factions.FactionJoinMode;
import com.yapcore.factions.FactionMember;
import com.yapcore.factions.FactionRelation;
import com.yapcore.factions.FactionRole;
import com.yapcore.factions.FactionTerritoryRules;
import com.yapcore.factions.integration.ClaimIntegration;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

final class FactionClaimGuardOps {

    private final FactionServiceSupport s;

    FactionClaimGuardOps(FactionServiceSupport support) {
        this.s = support;
    }

    CompletableFuture<FactionClaimOverlay> linkClaim(
            long claimId, long factionId, UUID actorId, int claimArea) {
        return CompletableFuture.supplyAsync(() -> linkClaimInternal(claimId, factionId, actorId, claimArea));
    }

    CompletableFuture<Integer> linkAllClaims(
            long factionId, UUID actorId, List<Long> claimIds, List<Integer> claimAreas) {
        return CompletableFuture.supplyAsync(() -> {
            int linked = 0;
            for (int i = 0; i < claimIds.size(); i++) {
                long claimId = claimIds.get(i);
                int area = claimAreas.get(i);
                try {
                    if (s.repository.overlay(claimId).isPresent()) {
                        continue;
                    }
                    linkClaimInternal(claimId, factionId, actorId, area);
                    linked++;
                } catch (RuntimeException | SQLException ignored) {
                    // stop when out of power
                    break;
                }
            }
            return linked;
        });
    }

    CompletableFuture<Void> unlinkClaim(long claimId, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                FactionClaimOverlay overlay = s.repository.overlay(claimId)
                        .orElseThrow(() -> new IllegalStateException("claim not linked"));
                FactionMember actor = s.requireMember(overlay.factionId(), actorId);
                if (!actor.role().atLeast(FactionRole.OFFICER)) {
                    throw new IllegalStateException("officer only");
                }
                s.repository.unlinkClaim(claimId);
                s.overlayCache.put(claimId, Optional.empty());
                s.refreshPower(overlay.factionId());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    Optional<Long> factionAt(Location location) {
        return ClaimIntegration.claimAt(location)
                .flatMap(c -> s.overlayForClaim(c.id()).map(FactionClaimOverlay::factionId));
    }

    Optional<Boolean> evaluateBuild(Player player, long claimId, UUID claimOwnerId) {
        return evaluateTerritory(player, claimId, claimOwnerId, null, true);
    }

    Optional<Boolean> evaluateContainer(Player player, long claimId, UUID claimOwnerId) {
        return evaluateTerritory(player, claimId, claimOwnerId, null, false);
    }

    private Optional<Boolean> evaluateTerritory(
            Player player, long claimId, UUID claimOwnerId, Player victim, boolean build) {
        Optional<FactionClaimOverlay> overlay = s.overlayForClaim(claimId);
        if (overlay.isEmpty()) {
            return Optional.empty();
        }
        long territoryId = overlay.get().factionId();
        Optional<Faction> territoryFaction = s.getFaction(territoryId);
        boolean shielded = territoryFaction.isPresent() && territoryFaction.get().isShielded();
        Optional<FactionMember> member = s.member(player.getUniqueId());
        Long actorFaction = member.map(FactionMember::factionId).orElse(null);
        FactionRelation actorToTerritory = actorFaction == null
                ? FactionRelation.NEUTRAL
                : s.relationBetween(actorFaction, territoryId);
        Long victimFaction = null;
        FactionRelation attackerToVictim = FactionRelation.NEUTRAL;
        if (victim != null) {
            Optional<FactionMember> vic = s.member(victim.getUniqueId());
            victimFaction = vic.map(FactionMember::factionId).orElse(null);
            if (actorFaction != null && victimFaction != null) {
                attackerToVictim = s.relationBetween(actorFaction, victimFaction);
            }
        }
        FactionTerritoryRules.Context ctx = new FactionTerritoryRules.Context(
                true,
                claimOwnerId.equals(player.getUniqueId()),
                shielded,
                s.config.shieldBlocksPvp(),
                actorFaction,
                victimFaction,
                territoryId,
                actorToTerritory,
                attackerToVictim,
                s.config.alliesCanBuild(),
                s.config.enemyPvpOnly(),
                s.config.membersCanOpenChests(),
                s.config.alliesCanOpenChests());
        if (build) {
            return FactionTerritoryRules.evaluateBuild(ctx);
        }
        return FactionTerritoryRules.evaluateContainer(ctx);
    }

    Optional<Boolean> evaluatePvp(Player attacker, Player victim, long claimId) {
        Optional<FactionClaimOverlay> overlay = s.overlayForClaim(claimId);
        if (overlay.isEmpty()) {
            return Optional.empty();
        }
        long territoryId = overlay.get().factionId();
        Optional<Faction> territoryFaction = s.getFaction(territoryId);
        boolean shielded = territoryFaction.isPresent() && territoryFaction.get().isShielded();
        Optional<FactionMember> atk = s.member(attacker.getUniqueId());
        Optional<FactionMember> vic = s.member(victim.getUniqueId());
        Long actorFaction = atk.map(FactionMember::factionId).orElse(null);
        Long victimFaction = vic.map(FactionMember::factionId).orElse(null);
        FactionRelation attackerToVictim = FactionRelation.NEUTRAL;
        if (actorFaction != null && victimFaction != null) {
            attackerToVictim = s.relationBetween(actorFaction, victimFaction);
        }
        FactionTerritoryRules.Context ctx = new FactionTerritoryRules.Context(
                true,
                false,
                shielded,
                s.config.shieldBlocksPvp(),
                actorFaction,
                victimFaction,
                territoryId,
                FactionRelation.NEUTRAL,
                attackerToVictim,
                s.config.alliesCanBuild(),
                s.config.enemyPvpOnly(),
                s.config.membersCanOpenChests(),
                s.config.alliesCanOpenChests());
        return FactionTerritoryRules.evaluatePvp(ctx);
    }

    Map<String, Object> dashboardSnapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", s.config.enabled());
        try {
            out.putAll(s.repository.dashboardCounts());
            List<Map<String, Object>> preview = s.repository.listAll().stream().limit(10).map(f -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", f.id());
                row.put("name", f.name());
                row.put("tag", f.tag());
                row.put("power", f.power());
                row.put("maxPower", f.maxPower());
                row.put("joinMode", f.joinMode().name());
                row.put("bank", f.bankBalance());
                row.put("shielded", f.isShielded());
                return row;
            }).toList();
            out.put("preview", preview);
        } catch (SQLException e) {
            out.put("error", e.getMessage());
        }
        return out;
    }

    void invalidateOverlay(long claimId) {
        s.overlayCache.remove(claimId);
    }

    void applyDeathPowerLoss(UUID playerId) {
        try {
            Optional<FactionMember> member = s.repository.member(playerId);
            if (member.isEmpty() || s.config.powerLossOnDeath() <= 0) {
                return;
            }
            long factionId = member.get().factionId();
            Faction faction = s.repository.get(factionId).orElse(null);
            if (faction == null) {
                return;
            }
            int used = s.repository.totalOverlayPower(factionId);
            int cap = Math.max(0, faction.maxPower() - used);
            int next = Math.max(0, faction.power() - s.config.powerLossOnDeath());
            s.repository.updatePowerOnly(factionId, next);
            if (next <= 0 && cap <= 0 && s.config.shieldSeconds() > 0) {
                s.repository.updateShield(factionId, Instant.now().plusSeconds(s.config.shieldSeconds()));
            }
        } catch (SQLException e) {
            s.plugin.getLogger().log(Level.WARNING, "applyDeathPowerLoss", e);
        }
    }

    void regenPowerTick() {
        try {
            for (Faction faction : s.repository.listAll()) {
                int used = s.repository.totalOverlayPower(faction.id());
                int cap = Math.max(0, faction.maxPower() - used);
                if (cap <= 0 || s.config.powerRegenAmount() <= 0) {
                    continue;
                }
                int next = Math.min(cap, faction.power() + s.config.powerRegenAmount());
                if (next != faction.power()) {
                    s.repository.updatePowerOnly(faction.id(), next);
                }
                if (next > 0 && faction.isShielded()) {
                    s.repository.updateShield(faction.id(), null);
                }
            }
        } catch (SQLException e) {
            s.plugin.getLogger().log(Level.WARNING, "regenPowerTick", e);
        }
    }

    void adminSetPower(String factionRef, int power, Integer maxPower) throws SQLException {
        Faction faction = s.resolveFactionRef(factionRef).orElseThrow(() -> new IllegalStateException("faction not found"));
        int max = maxPower == null ? faction.maxPower() : maxPower;
        s.repository.updatePower(faction.id(), power, max);
    }

    void adminSetJoinMode(String factionRef, FactionJoinMode mode) throws SQLException {
        Faction faction = s.resolveFactionRef(factionRef).orElseThrow(() -> new IllegalStateException("faction not found"));
        s.repository.updateJoinMode(faction.id(), mode);
    }

    void adminForceDisband(String factionRef) throws SQLException {
        Faction faction = s.resolveFactionRef(factionRef).orElseThrow(() -> new IllegalStateException("faction not found"));
        for (FactionMember m : s.repository.members(faction.id())) {
            s.chatState.clear(m.playerId());
            s.clearMemberPerks(m.playerId(), faction);
        }
        s.repository.deleteFaction(faction.id());
        s.overlayCache.clear();
    }

    private FactionClaimOverlay linkClaimInternal(long claimId, long factionId, UUID actorId, int claimArea) {
        try {
            FactionMember actor = s.requireMember(factionId, actorId);
            if (!actor.role().atLeast(FactionRole.OFFICER)) {
                throw new IllegalStateException("officer only");
            }
            int cost = s.claimPowerCost(claimArea);
            Faction faction = s.repository.get(factionId).orElseThrow();
            int used = s.repository.totalOverlayPower(factionId);
            if (!FactionTerritoryRules.canAffordClaim(used, cost, faction.maxPower())) {
                throw new IllegalStateException("not enough faction power");
            }
            FactionClaimOverlay overlay = new FactionClaimOverlay(claimId, factionId, cost, Instant.now());
            s.repository.linkClaim(overlay);
            s.overlayCache.put(claimId, Optional.of(overlay));
            s.refreshPower(factionId);
            return overlay;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
