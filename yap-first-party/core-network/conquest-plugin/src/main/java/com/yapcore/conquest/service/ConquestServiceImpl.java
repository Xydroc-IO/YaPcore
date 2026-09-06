package com.yapcore.conquest.service;

import com.yapcore.conquest.ConquestChunk;
import com.yapcore.conquest.ConquestCombatTagTracker;
import com.yapcore.conquest.ConquestConfig;
import com.yapcore.conquest.ConquestExplosionRules;
import com.yapcore.conquest.ConquestOverclaimRules;
import com.yapcore.conquest.ConquestService;
import com.yapcore.conquest.ConquestTerritoryRules;
import com.yapcore.conquest.ConquestZoneRules;
import com.yapcore.conquest.ConquestZoneType;
import com.yapcore.conquest.db.ConquestRepository;
import com.yapcore.conquest.db.ConquestZoneRepository;
import com.yapcore.factions.Faction;
import com.yapcore.factions.FactionMember;
import com.yapcore.factions.FactionRelation;
import com.yapcore.factions.FactionRole;
import com.yapcore.factions.FactionService;
import com.yapcore.factions.FactionServices;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ConquestServiceImpl implements ConquestService {

    private final JavaPlugin plugin;
    private final ConquestConfig config;
    private final ConquestRepository repository;
    private final ConquestZoneRepository zones;
    private final ConquestCombatTagTracker combatTags = new ConquestCombatTagTracker();

    public ConquestServiceImpl(
            JavaPlugin plugin,
            ConquestConfig config,
            ConquestRepository repository,
            ConquestZoneRepository zones) {
        this.plugin = plugin;
        this.config = config;
        this.repository = repository;
        this.zones = zones;
    }

    public ConquestCombatTagTracker combatTags() {
        return combatTags;
    }

    @Override
    public boolean isEnabled() {
        return config.enabled();
    }

    @Override
    public boolean zonesEnabled() {
        return config.zonesEnabled();
    }

    @Override
    public boolean overclaimEnabled() {
        return config.overclaimEnabled();
    }

    @Override
    public boolean flyEnabled() {
        return config.flyEnabled();
    }

    @Override
    public boolean combatTagEnabled() {
        return config.combatTagEnabled();
    }

    @Override
    public int claimCost() {
        return config.claimCost();
    }

    @Override
    public Optional<ConquestChunk> chunkAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        return chunk(location.getWorld().getName(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    @Override
    public Optional<ConquestChunk> chunk(String world, int chunkX, int chunkZ) {
        try {
            return repository.find(world, chunkX, chunkZ);
        } catch (Exception e) {
            plugin.getLogger().warning("Conquest chunk lookup failed: " + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<ConquestChunk> chunksForFaction(long factionId) {
        try {
            return repository.listForFaction(factionId);
        } catch (Exception e) {
            plugin.getLogger().warning("Conquest list failed: " + e.getMessage());
            return List.of();
        }
    }

    @Override
    public int totalPowerUsed(long factionId) {
        try {
            return repository.sumPowerCost(factionId);
        } catch (Exception e) {
            plugin.getLogger().warning("Conquest power sum failed: " + e.getMessage());
            return 0;
        }
    }

    @Override
    public Optional<ConquestZoneType> zoneAt(Location location) {
        if (!config.zonesEnabled() || location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        return Optional.of(resolveZone(
                location.getWorld().getName(), location.getBlockX() >> 4, location.getBlockZ() >> 4));
    }

    @Override
    public Optional<ConquestZoneType> zone(String world, int chunkX, int chunkZ) {
        if (!config.zonesEnabled()) {
            return Optional.empty();
        }
        return Optional.of(resolveZone(world, chunkX, chunkZ));
    }

    @Override
    public CompletableFuture<Void> setChunkZone(String world, int chunkX, int chunkZ, ConquestZoneType type) {
        return CompletableFuture.runAsync(() -> {
            if (!config.zonesEnabled()) {
                throw new IllegalStateException("Zones are disabled. Set zones.enabled: true then reload.");
            }
            try {
                if (repository.find(world, chunkX, chunkZ).isPresent()) {
                    throw new IllegalStateException("Clear the faction claim before setting a zone on this chunk.");
                }
                zones.upsert(world, chunkX, chunkZ, type);
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("Set zone failed: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> clearChunkZone(String world, int chunkX, int chunkZ) {
        return CompletableFuture.runAsync(() -> {
            if (!config.zonesEnabled()) {
                throw new IllegalStateException("Zones are disabled. Set zones.enabled: true then reload.");
            }
            try {
                zones.delete(world, chunkX, chunkZ);
            } catch (Exception e) {
                throw new IllegalStateException("Clear zone failed: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<ConquestChunk> claim(Player player, Location location) {
        return CompletableFuture.supplyAsync(() -> claimSync(player, location));
    }

    @Override
    public CompletableFuture<Void> unclaim(Player player, Location location) {
        return CompletableFuture.runAsync(() -> unclaimSync(player, location));
    }

    @Override
    public Optional<Boolean> evaluateBuild(Player player, Location location) {
        Optional<ConquestChunk> land = chunkAt(location);
        Optional<Boolean> factionBuild = Optional.empty();
        if (land.isPresent()) {
            factionBuild = factionEvaluateBuild(player, land.get());
        }
        if (config.zonesEnabled()) {
            ConquestZoneType zone = zoneAt(location).orElse(ConquestZoneType.WILDERNESS);
            return ConquestZoneRules.evaluateBuild(
                    zone, config.zoneSettings(), land.isPresent(), factionBuild);
        }
        return land.isPresent() ? factionBuild : Optional.empty();
    }

    @Override
    public Optional<Boolean> evaluatePvp(Player attacker, Player victim, Location location) {
        Optional<ConquestChunk> land = chunkAt(location);
        Optional<Boolean> factionPvp = Optional.empty();
        if (land.isPresent()) {
            factionPvp = factionEvaluatePvp(attacker, victim, land.get());
        }
        if (config.zonesEnabled()) {
            ConquestZoneType zone = zoneAt(location).orElse(ConquestZoneType.WILDERNESS);
            return ConquestZoneRules.evaluatePvp(
                    zone, config.zoneSettings(), land.isPresent(), factionPvp);
        }
        return land.isPresent() ? factionPvp : Optional.empty();
    }

    @Override
    public Optional<Boolean> evaluateExplode(Location location) {
        Optional<Boolean> zoneExplode = Optional.empty();
        if (config.zonesEnabled()) {
            ConquestZoneType zone = zoneAt(location).orElse(ConquestZoneType.WILDERNESS);
            zoneExplode = ConquestZoneRules.evaluateExplode(zone, config.zoneSettings());
        }
        boolean claimed = chunkAt(location).isPresent();
        return ConquestExplosionRules.evaluate(
                config.explosionsEnabled(), claimed, config.explosionsClaimed(), zoneExplode);
    }

    @Override
    public boolean isOwnTerritory(Player player, Location location) {
        Optional<ConquestChunk> land = chunkAt(location);
        if (land.isEmpty()) {
            return false;
        }
        return FactionServices.find()
                .flatMap(fs -> fs.member(player.getUniqueId()))
                .map(m -> m.factionId() == land.get().factionId())
                .orElse(false);
    }

    @Override
    public boolean isCombatTagged(UUID playerId) {
        return config.combatTagEnabled() && combatTags.isTagged(playerId, Instant.now());
    }

    @Override
    public void tagCombat(UUID playerId) {
        if (!config.combatTagEnabled()) {
            return;
        }
        combatTags.tag(playerId, Instant.now(), config.combatTagSeconds());
    }

    private Optional<Boolean> factionEvaluateBuild(Player player, ConquestChunk land) {
        FactionService factions = requireFactionsOptional().orElse(null);
        if (factions == null) {
            return Optional.of(false);
        }
        Optional<FactionMember> member = factions.member(player.getUniqueId());
        Long actorFaction = member.map(FactionMember::factionId).orElse(null);
        FactionRelation toTerritory = FactionRelation.NEUTRAL;
        if (actorFaction != null) {
            toTerritory = factions.relationBetween(actorFaction, land.factionId());
        }
        boolean frozen = land.frozen() || territoryShielded(factions, land.factionId());
        return ConquestTerritoryRules.evaluateBuild(new ConquestTerritoryRules.Context(
                true,
                frozen,
                actorFaction,
                null,
                land.factionId(),
                toTerritory,
                FactionRelation.NEUTRAL,
                config.alliesCanBuild(),
                config.enemyPvpOnly()));
    }

    private Optional<Boolean> factionEvaluatePvp(Player attacker, Player victim, ConquestChunk land) {
        FactionService factions = requireFactionsOptional().orElse(null);
        if (factions == null) {
            return Optional.empty();
        }
        Optional<FactionMember> atk = factions.member(attacker.getUniqueId());
        Optional<FactionMember> vic = factions.member(victim.getUniqueId());
        Long atkFaction = atk.map(FactionMember::factionId).orElse(null);
        Long vicFaction = vic.map(FactionMember::factionId).orElse(null);
        FactionRelation atkToVic = FactionRelation.NEUTRAL;
        if (atkFaction != null && vicFaction != null) {
            atkToVic = factions.relationBetween(atkFaction, vicFaction);
        }
        FactionRelation toTerritory = FactionRelation.NEUTRAL;
        if (atkFaction != null) {
            toTerritory = factions.relationBetween(atkFaction, land.factionId());
        }
        boolean frozen = land.frozen()
                || (config.shieldBlocksPvp() && territoryShielded(factions, land.factionId()));
        return ConquestTerritoryRules.evaluatePvp(new ConquestTerritoryRules.Context(
                true,
                frozen,
                atkFaction,
                vicFaction,
                land.factionId(),
                toTerritory,
                atkToVic,
                config.alliesCanBuild(),
                config.enemyPvpOnly()));
    }

    private ConquestChunk claimSync(Player player, Location location) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("Invalid location.");
        }
        FactionService factions = requireFactions();
        FactionMember member = factions.member(player.getUniqueId())
                .orElseThrow(() -> new IllegalStateException("Join a faction before claiming conquest land."));
        if (!member.role().atLeast(FactionRole.OFFICER)
                && !player.hasPermission("yapconquest.admin")) {
            throw new IllegalStateException("Officers or leaders claim conquest chunks.");
        }
        Faction faction = factions.getFaction(member.factionId())
                .orElseThrow(() -> new IllegalStateException("Faction not found."));
        String world = location.getWorld().getName();
        int cx = location.getBlockX() >> 4;
        int cz = location.getBlockZ() >> 4;
        try {
            if (config.zonesEnabled()) {
                ConquestZoneType zone = resolveZone(world, cx, cz);
                if (!ConquestZoneRules.canClaim(zone, config.zoneSettings())) {
                    throw new IllegalStateException("Cannot claim in " + zone.name().toLowerCase() + ".");
                }
            }
            Optional<ConquestChunk> existing = repository.find(world, cx, cz);
            int cost = config.claimCost();
            int used = repository.sumPowerCost(faction.id());

            if (existing.isPresent()) {
                return overclaimSync(factions, faction, member, existing.get(), used, cost);
            }
            if (!ConquestTerritoryRules.canAfford(used, cost, faction.maxPower())) {
                throw new IllegalStateException("Not enough faction power (need " + cost
                        + ", used " + used + "/" + faction.maxPower() + ").");
            }
            if (config.zonesEnabled()) {
                zones.delete(world, cx, cz);
            }
            ConquestChunk chunk = new ConquestChunk(
                    world, cx, cz, faction.id(), cost, Instant.now(), false);
            repository.insert(chunk);
            return chunk;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Claim failed: " + e.getMessage(), e);
        }
    }

    private ConquestChunk overclaimSync(
            FactionService factions,
            Faction attackerFaction,
            FactionMember member,
            ConquestChunk existing,
            int attackerUsed,
            int cost) throws Exception {
        if (existing.factionId() == attackerFaction.id()) {
            throw new IllegalStateException("Your faction already owns this chunk.");
        }
        if (!config.overclaimEnabled()) {
            throw new IllegalStateException("This chunk is already claimed.");
        }
        Faction defender = factions.getFaction(existing.factionId())
                .orElseThrow(() -> new IllegalStateException("Defending faction missing."));
        boolean isEnemy = factions.relationBetween(attackerFaction.id(), defender.id())
                == FactionRelation.ENEMY;
        int defenderLand = repository.sumPowerCost(defender.id());
        if (!ConquestOverclaimRules.canOverclaim(
                true,
                config.overclaimRequireEnemy(),
                isEnemy,
                defender.isShielded(),
                existing.frozen(),
                defender.power(),
                defenderLand,
                attackerUsed,
                cost,
                attackerFaction.maxPower())) {
            if (config.overclaimRequireEnemy() && !isEnemy) {
                throw new IllegalStateException("Must be enemies to overclaim.");
            }
            if (defender.isShielded() || existing.frozen()) {
                throw new IllegalStateException("This land is shielded or frozen.");
            }
            if (!ConquestOverclaimRules.isVulnerable(defender.power(), defenderLand)) {
                throw new IllegalStateException("Defender is not overclaimable (power ≥ land).");
            }
            throw new IllegalStateException("Cannot overclaim this chunk.");
        }
        Instant now = Instant.now();
        repository.transfer(existing.world(), existing.chunkX(), existing.chunkZ(),
                attackerFaction.id(), cost, now);
        return new ConquestChunk(
                existing.world(), existing.chunkX(), existing.chunkZ(),
                attackerFaction.id(), cost, now, false);
    }

    private void unclaimSync(Player player, Location location) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("Invalid location.");
        }
        FactionService factions = requireFactions();
        FactionMember member = factions.member(player.getUniqueId())
                .orElseThrow(() -> new IllegalStateException("Join a faction before unclaiming."));
        if (!member.role().atLeast(FactionRole.OFFICER)
                && !player.hasPermission("yapconquest.admin")) {
            throw new IllegalStateException("Officers or leaders unclaim conquest chunks.");
        }
        String world = location.getWorld().getName();
        int cx = location.getBlockX() >> 4;
        int cz = location.getBlockZ() >> 4;
        try {
            ConquestChunk chunk = repository.find(world, cx, cz)
                    .orElseThrow(() -> new IllegalStateException("This chunk is not conquest land."));
            if (chunk.factionId() != member.factionId() && !player.hasPermission("yapconquest.admin")) {
                throw new IllegalStateException("Only the owning faction can unclaim this chunk.");
            }
            repository.delete(world, cx, cz);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Unclaim failed: " + e.getMessage(), e);
        }
    }

    private ConquestZoneType resolveZone(String world, int chunkX, int chunkZ) {
        try {
            Optional<ConquestZoneType> override = zones.find(world, chunkX, chunkZ);
            if (override.isPresent()) {
                return override.get();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Conquest zone lookup failed: " + e.getMessage());
        }
        return config.worldDefaultZone(world);
    }

    private static FactionService requireFactions() {
        return FactionServices.find()
                .orElseThrow(() -> new IllegalStateException(
                        "YaPFactions is required. Enable YaPFactions (enabled: true) and reload."));
    }

    private static Optional<FactionService> requireFactionsOptional() {
        return FactionServices.find();
    }

    private static boolean territoryShielded(FactionService factions, long factionId) {
        return factions.getFaction(factionId).map(Faction::isShielded).orElse(false);
    }
}
