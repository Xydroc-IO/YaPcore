package com.yapcore.playerdata.claims;

import com.yapcore.factions.FactionService;
import com.yapcore.factions.FactionServices;
import com.yapcore.playerdata.PlayerDataConfig;
import com.yapcore.playerdata.db.ClaimRepository;
import com.yapcore.regions.FlagValue;
import com.yapcore.regions.RegionFlag;
import com.yapcore.sched.StaffBypass;
import com.yapcore.sched.YapSched;
import com.yapcore.sched.YapTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Claim index, subdivides, shovel selection, trust, claim-block accrual.
 */
public final class ClaimService {

    public enum SelectMode {
        CLAIM, SUBDIVIDE
    }

    public record Corner(String world, int x, int z) {
    }

    private final JavaPlugin plugin;
    private final PlayerDataConfig config;
    private final ClaimRepository repo;
    private final ClaimFlagService flags;

    private final List<Claim> local = new ArrayList<>();
    private final Map<UUID, Corner> pending = new ConcurrentHashMap<>();
    private final Map<UUID, SelectMode> modes = new ConcurrentHashMap<>();
    private final Map<Long, Map<UUID, ClaimRepository.TrustLevel>> trustCache = new ConcurrentHashMap<>();
    private YapTask accrualTask;
    private final ClaimCreationOps creation;

    public ClaimService(JavaPlugin plugin, PlayerDataConfig config, ClaimRepository repo,
                        ClaimFlagService flags) {
        this.plugin = plugin;
        this.config = config;
        this.repo = repo;
        this.flags = flags;
        this.creation = new ClaimCreationOps(this);
    }

    public ClaimFlagService flags() {
        return flags;
    }

    public void start() {
        reloadLocal();
        if (config.claimsBlocksPerHour() > 0) {
            long period = 20L * 60L;
            accrualTask = YapSched.asyncTimer(plugin, () -> {
                int perMin = Math.max(1, config.claimsBlocksPerHour() / 60);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    try {
                        int cur = repo.getBlocks(p.getUniqueId(), config.claimsStartingBlocks());
                        repo.setBlocks(p.getUniqueId(), cur + perMin);
                    } catch (SQLException e) {
                        plugin.getLogger().log(Level.FINE, "claim block accrual", e);
                    }
                }
            }, period, period);
        }
    }

    public void stop() {
        if (accrualTask != null) {
            accrualTask.cancel();
            accrualTask = null;
        }
        local.clear();
        pending.clear();
        modes.clear();
        trustCache.clear();
        flags.invalidateAll();
    }

    public void reloadLocal() {
        try {
            List<Claim> loaded = repo.listForServer(config.serverId());
            synchronized (local) {
                local.clear();
                local.addAll(loaded);
            }
            trustCache.clear();
            plugin.getLogger().info("Loaded " + loaded.size() + " claims for " + config.serverId());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load claims", e);
        }
    }

    public void setMode(UUID uuid, SelectMode mode) {
        modes.put(uuid, mode);
        pending.remove(uuid);
    }

    public SelectMode mode(UUID uuid) {
        return modes.getOrDefault(uuid, SelectMode.CLAIM);
    }

    /** Deepest / smallest claim at location (subclaims win). */
    public Optional<Claim> getAt(Location loc) {
        if (loc.getWorld() == null) {
            return Optional.empty();
        }
        String world = loc.getWorld().getName();
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        Claim best = null;
        synchronized (local) {
            for (Claim c : local) {
                if (!c.contains(world, x, z)) {
                    continue;
                }
                if (best == null || c.area() < best.area()) {
                    best = c;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** Claims owned by or manageable by the player on this server. */
    public List<Claim> manageableBy(Player player) {
        UUID id = player.getUniqueId();
        synchronized (local) {
            return local.stream()
                    .filter(c -> c.owner().equals(id) || hasTrust(c, id, ClaimRepository.TrustLevel.MANAGE))
                    .toList();
        }
    }

    public Optional<Claim> getTopLevelAt(Location loc) {
        Optional<Claim> at = getAt(loc);
        if (at.isEmpty()) {
            return Optional.empty();
        }
        Claim c = at.get();
        if (!c.isSubdivision()) {
            return at;
        }
        synchronized (local) {
            for (Claim p : local) {
                if (p.id() == c.parentId()) {
                    return Optional.of(p);
                }
            }
        }
        try {
            return repo.get(c.parentId());
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    public List<Claim> localClaims() {
        synchronized (local) {
            return List.copyOf(local);
        }
    }

    public void updateLocal(Claim claim) {
        synchronized (local) {
            local.removeIf(c -> c.id() == claim.id());
            local.add(claim);
        }
    }

    public boolean canBuild(Player player, Location loc) {
        if (!config.claimsEnabled()) {
            return true;
        }
        if (StaffBypass.land(player)) {
            return true;
        }
        Optional<Claim> claim = getAt(loc);
        if (claim.isEmpty()) {
            return !config.claimsRequireClaimToBuild() || player.hasPermission("yapdata.claims.wilderness");
        }
        Claim c = claim.get();
        Optional<Boolean> factionBuild = factionBuildOverride(player, c);
        if (factionBuild.isPresent()) {
            return factionBuild.get();
        }
        if (!flagAllowsBuild(c, player)) {
            return false;
        }
        if (c.taxFrozen()) {
            return false;
        }
        Claim check = c;
        if (c.isSubdivision()) {
            // parent frozen freezes subs
            Optional<Claim> parent = getTopLevelAt(loc);
            if (parent.isPresent() && parent.get().taxFrozen()) {
                return false;
            }
        }
        return hasTrust(check, player.getUniqueId(), ClaimRepository.TrustLevel.BUILD);
    }

    public boolean canAccess(Player player, Location loc) {
        if (!config.claimsEnabled()) {
            return true;
        }
        if (StaffBypass.land(player)) {
            return true;
        }
        Optional<Claim> claim = getAt(loc);
        if (claim.isEmpty()) {
            return true;
        }
        if (claim.get().taxFrozen() && !claim.get().owner().equals(player.getUniqueId())) {
            return false;
        }
        if (!flagAllowsInteract(claim.get(), player)) {
            return false;
        }
        return hasTrust(claim.get(), player.getUniqueId(), ClaimRepository.TrustLevel.ACCESS);
    }

    public boolean canEnter(Player player, Location loc) {
        if (!config.claimsEnabled() || StaffBypass.land(player)) {
            return true;
        }
        Optional<Claim> claim = getAt(loc);
        if (claim.isEmpty()) {
            return true;
        }
        FlagValue entry = flags.resolveOrDefault(claim.get().id(), RegionFlag.ENTRY);
        if (entry == FlagValue.ALLOW) {
            return true;
        }
        return hasTrust(claim.get(), player.getUniqueId(), ClaimRepository.TrustLevel.ACCESS);
    }

    public boolean isPvpAllowed(Player attacker, Player victim) {
        if (!config.claimsEnabled()) {
            return true;
        }
        var claim = getAt(victim.getLocation());
        if (claim.isEmpty()) {
            return true;
        }
        Optional<Boolean> factionPvp = factionPvpOverride(attacker, victim, claim.get().id());
        if (factionPvp.isPresent()) {
            return factionPvp.get();
        }
        FlagValue pvp = flags.resolveOrDefault(claim.get().id(), RegionFlag.PVP);
        if (pvp == FlagValue.DENY) {
            return StaffBypass.land(attacker)
                    || hasTrust(claim.get(), attacker.getUniqueId(), ClaimRepository.TrustLevel.BUILD);
        }
        return true;
    }

    private Optional<Boolean> factionBuildOverride(Player player, Claim claim) {
        Optional<FactionService> factions = FactionServices.find();
        if (factions.isEmpty()) {
            return Optional.empty();
        }
        return factions.get().evaluateBuild(player, claim.id(), claim.owner());
    }

    private Optional<Boolean> factionPvpOverride(Player attacker, Player victim, long claimId) {
        Optional<FactionService> factions = FactionServices.find();
        if (factions.isEmpty()) {
            return Optional.empty();
        }
        return factions.get().evaluatePvp(attacker, victim, claimId);
    }

    public boolean isMobDamageAllowed(org.bukkit.entity.Player victim) {
        if (!config.claimsEnabled()) {
            return true;
        }
        var claim = getAt(victim.getLocation());
        if (claim.isEmpty()) {
            return true;
        }
        return flags.resolveOrDefault(claim.get().id(), RegionFlag.MOB_DAMAGE) == FlagValue.ALLOW;
    }

    public boolean isFireSpreadAllowed(Location loc) {
        if (!config.claimsEnabled()) {
            return true;
        }
        var claim = getAt(loc);
        if (claim.isEmpty()) {
            return true;
        }
        return flags.resolveOrDefault(claim.get().id(), RegionFlag.FIRE_SPREAD) == FlagValue.ALLOW;
    }

    public boolean isMobSpawningAllowed(Location loc) {
        if (!config.claimsEnabled()) {
            return true;
        }
        var claim = getAt(loc);
        if (claim.isEmpty()) {
            return true;
        }
        return flags.resolveOrDefault(claim.get().id(), RegionFlag.MOB_SPAWNING) == FlagValue.ALLOW;
    }

    private boolean flagAllowsBuild(Claim claim, Player player) {
        var explicit = flags.explicit(claim.id(), RegionFlag.BUILD);
        if (explicit.isPresent() && explicit.get() == FlagValue.DENY) {
            return false;
        }
        if (explicit.isPresent() && explicit.get() == FlagValue.ALLOW) {
            return hasTrust(claim, player.getUniqueId(), ClaimRepository.TrustLevel.BUILD);
        }
        return true;
    }

    private boolean flagAllowsInteract(Claim claim, Player player) {
        var explicit = flags.explicit(claim.id(), RegionFlag.INTERACT);
        if (explicit.isPresent() && explicit.get() == FlagValue.DENY) {
            return false;
        }
        return true;
    }

    public boolean canOpenContainer(Player player, Location loc) {
        if (!canAccess(player, loc)) {
            return false;
        }
        Optional<Claim> claim = getAt(loc);
        if (claim.isEmpty()) {
            return true;
        }
        FlagValue chest = flags.resolveOrDefault(claim.get().id(), RegionFlag.CHEST_ACCESS);
        if (chest == FlagValue.DENY) {
            return false;
        }
        return hasTrust(claim.get(), player.getUniqueId(), ClaimRepository.TrustLevel.ACCESS);
    }

    public boolean hasTrust(Claim claim, UUID player, ClaimRepository.TrustLevel needed) {
        if (claim.owner().equals(player)) {
            return true;
        }
        // subclaim trust first; fall back to parent trust
        Map<UUID, ClaimRepository.TrustLevel> map = trustCache.computeIfAbsent(claim.id(), id -> {
            try {
                return new ConcurrentHashMap<>(repo.trustMap(id));
            } catch (SQLException e) {
                return new ConcurrentHashMap<>();
            }
        });
        ClaimRepository.TrustLevel level = map.get(player);
        if (level != null && level.atLeast(needed)) {
            return true;
        }
        if (claim.isSubdivision()) {
            try {
                Optional<Claim> parent = repo.get(claim.parentId());
                if (parent.isPresent() && parent.get().owner().equals(player)) {
                    return true;
                }
                if (parent.isPresent()) {
                    return hasTrustDirect(parent.get(), player, needed);
                }
            } catch (SQLException ignored) {
            }
        }
        return false;
    }

    private boolean hasTrustDirect(Claim claim, UUID player, ClaimRepository.TrustLevel needed) {
        if (claim.owner().equals(player)) {
            return true;
        }
        Map<UUID, ClaimRepository.TrustLevel> map = trustCache.computeIfAbsent(claim.id(), id -> {
            try {
                return new ConcurrentHashMap<>(repo.trustMap(id));
            } catch (SQLException e) {
                return new ConcurrentHashMap<>();
            }
        });
        ClaimRepository.TrustLevel level = map.get(player);
        return level != null && level.atLeast(needed);
    }

    public void invalidateTrust(long claimId) {
        trustCache.remove(claimId);
    }

    public String handleShovel(Player player, Location loc) throws SQLException {
        if (!config.claimsEnabled()) {
            return "§cClaims are disabled.";
        }
        SelectMode mode = mode(player.getUniqueId());
        Corner first = pending.get(player.getUniqueId());
        if (first == null) {
            pending.put(player.getUniqueId(), new Corner(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockZ()));
            String tip = mode == SelectMode.SUBDIVIDE ? "subdivision" : "claim";
            return "§a" + tip + " corner #1 set. Click opposite corner with shovel.";
        }
        if (!first.world().equals(loc.getWorld().getName())) {
            pending.remove(player.getUniqueId());
            return "§cCorners must be in the same world. Selection cleared.";
        }
        int minX = Math.min(first.x(), loc.getBlockX());
        int maxX = Math.max(first.x(), loc.getBlockX());
        int minZ = Math.min(first.z(), loc.getBlockZ());
        int maxZ = Math.max(first.z(), loc.getBlockZ());
        int area = (maxX - minX + 1) * (maxZ - minZ + 1);
        pending.remove(player.getUniqueId());

        if (mode == SelectMode.SUBDIVIDE) {
            return creation.createSubdivision(player, loc.getWorld().getName(), minX, maxX, minZ, maxZ, area);
        }
        return creation.createTopLevel(player, loc.getWorld().getName(), minX, maxX, minZ, maxZ, area);
    }

    public boolean abandon(Player player, Claim claim) throws SQLException {
        if (!claim.owner().equals(player.getUniqueId()) && !StaffBypass.land(player)) {
            return false;
        }
        int refund = claim.isSubdivision() ? 0 : claim.area();
        if (!repo.delete(claim.id())) {
            return false;
        }
        synchronized (local) {
            local.removeIf(c -> c.id() == claim.id()
                    || (c.parentId() != null && c.parentId() == claim.id()));
        }
        trustCache.remove(claim.id());
        if (refund > 0 && claim.owner().equals(player.getUniqueId())) {
            int cur = repo.getBlocks(player.getUniqueId(), config.claimsStartingBlocks());
            repo.setBlocks(player.getUniqueId(), cur + refund);
        }
        return true;
    }

    public ClaimRepository repo() {
        return repo;
    }

    public PlayerDataConfig config() {
        return config;
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    List<Claim> localClaimsMutable() {
        return local;
    }

    java.util.Map<UUID, SelectMode> modesMutable() {
        return modes;
    }
}
