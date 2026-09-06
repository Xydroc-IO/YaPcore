package com.yapcore.conquest;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface ConquestService {

    Optional<ConquestChunk> chunkAt(Location location);

    Optional<ConquestChunk> chunk(String world, int chunkX, int chunkZ);

    List<ConquestChunk> chunksForFaction(long factionId);

    int totalPowerUsed(long factionId);

    int claimCost();

    CompletableFuture<ConquestChunk> claim(Player player, Location location);

    CompletableFuture<Void> unclaim(Player player, Location location);

    Optional<ConquestZoneType> zoneAt(Location location);

    Optional<ConquestZoneType> zone(String world, int chunkX, int chunkZ);

    CompletableFuture<Void> setChunkZone(String world, int chunkX, int chunkZ, ConquestZoneType type);

    CompletableFuture<Void> clearChunkZone(String world, int chunkX, int chunkZ);

    Optional<Boolean> evaluateBuild(Player player, Location location);

    Optional<Boolean> evaluatePvp(Player attacker, Player victim, Location location);

    Optional<Boolean> evaluateExplode(Location location);

    /** True when standing in own faction claim (for fly). */
    boolean isOwnTerritory(Player player, Location location);

    boolean isCombatTagged(UUID playerId);

    void tagCombat(UUID playerId);

    boolean isEnabled();

    boolean zonesEnabled();

    boolean overclaimEnabled();

    boolean flyEnabled();

    boolean combatTagEnabled();
}
