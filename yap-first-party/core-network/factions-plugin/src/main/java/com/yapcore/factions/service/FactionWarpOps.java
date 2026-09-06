package com.yapcore.factions.service;

import com.yapcore.factions.Faction;
import com.yapcore.factions.FactionMember;
import com.yapcore.factions.FactionRole;
import com.yapcore.factions.FactionWarp;
import com.yapcore.factions.db.FactionWarpQueries;
import com.yapcore.factions.integration.ClaimIntegration;
import org.bukkit.Location;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class FactionWarpOps {

    private final FactionServiceSupport s;

    FactionWarpOps(FactionServiceSupport support) {
        this.s = support;
    }

    CompletableFuture<Void> setWarp(long factionId, UUID actorId, String rawName, Location location) {
        return CompletableFuture.runAsync(() -> {
            try {
                FactionMember actor = s.requireMember(factionId, actorId);
                if (!actor.role().atLeast(FactionRole.OFFICER)) {
                    throw new IllegalStateException("officer only");
                }
                String name = FactionWarpQueries.normalizeName(rawName);
                if (name.isBlank() || name.length() > s.config.warpNameMax()) {
                    throw new IllegalArgumentException("invalid warp name");
                }
                if (location.getWorld() == null) {
                    throw new IllegalStateException("invalid location");
                }
                if (s.config.warpsRequireInTerritory()) {
                    var claim = ClaimIntegration.claimAt(location);
                    if (claim.isEmpty()) {
                        throw new IllegalStateException("warp must be inside linked territory");
                    }
                    var overlay = s.overlayForClaim(claim.get().id());
                    if (overlay.isEmpty() || overlay.get().factionId() != factionId) {
                        throw new IllegalStateException("warp must be inside linked territory");
                    }
                }
                if (s.repository.warp(factionId, name).isEmpty()
                        && s.repository.warpCount(factionId) >= s.config.maxWarps()) {
                    throw new IllegalStateException("warp limit reached");
                }
                s.repository.upsertWarp(new FactionWarp(
                        factionId,
                        name,
                        location.getWorld().getName(),
                        location.getX(),
                        location.getY(),
                        location.getZ(),
                        location.getYaw(),
                        location.getPitch()));
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    CompletableFuture<Void> deleteWarp(long factionId, UUID actorId, String rawName) {
        return CompletableFuture.runAsync(() -> {
            try {
                FactionMember actor = s.requireMember(factionId, actorId);
                if (!actor.role().atLeast(FactionRole.OFFICER)) {
                    throw new IllegalStateException("officer only");
                }
                String name = FactionWarpQueries.normalizeName(rawName);
                if (s.repository.warp(factionId, name).isEmpty()) {
                    throw new IllegalStateException("warp not found");
                }
                s.repository.deleteWarp(factionId, name);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    List<FactionWarp> listWarps(long factionId) {
        try {
            return s.repository.listWarps(factionId);
        } catch (SQLException e) {
            return List.of();
        }
    }

    java.util.Optional<FactionWarp> warp(long factionId, String name) {
        try {
            return s.repository.warp(factionId, name);
        } catch (SQLException e) {
            return java.util.Optional.empty();
        }
    }
}
