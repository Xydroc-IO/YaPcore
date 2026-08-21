package com.yapcore.playerdata.claims;

import com.yapcore.playerdata.PlayerDataConfig;
import com.yapcore.playerdata.db.ClaimRepository;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

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

    private final List<Claim> local = new ArrayList<>();
    private final Map<UUID, Corner> pending = new ConcurrentHashMap<>();
    private final Map<UUID, SelectMode> modes = new ConcurrentHashMap<>();
    private final Map<Long, Map<UUID, ClaimRepository.TrustLevel>> trustCache = new ConcurrentHashMap<>();
    private BukkitTask accrualTask;

    public ClaimService(JavaPlugin plugin, PlayerDataConfig config, ClaimRepository repo) {
        this.plugin = plugin;
        this.config = config;
        this.repo = repo;
    }

    public void start() {
        reloadLocal();
        if (config.claimsBlocksPerHour() > 0) {
            long period = 20L * 60L;
            accrualTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
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
        if (player.hasPermission("yapdata.claims.admin")) {
            return true;
        }
        Optional<Claim> claim = getAt(loc);
        if (claim.isEmpty()) {
            return !config.claimsRequireClaimToBuild() || player.hasPermission("yapdata.claims.wilderness");
        }
        Claim c = claim.get();
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
        if (player.hasPermission("yapdata.claims.admin")) {
            return true;
        }
        Optional<Claim> claim = getAt(loc);
        if (claim.isEmpty()) {
            return true;
        }
        if (claim.get().taxFrozen() && !claim.get().owner().equals(player.getUniqueId())) {
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
            return createSubdivision(player, loc.getWorld().getName(), minX, maxX, minZ, maxZ, area);
        }
        return createTopLevel(player, loc.getWorld().getName(), minX, maxX, minZ, maxZ, area);
    }

    private String createTopLevel(Player player, String world, int minX, int maxX, int minZ, int maxZ, int area)
            throws SQLException {
        if (area < config.claimsMinArea()) {
            return "§cClaim too small (min " + config.claimsMinArea() + ").";
        }
        if (area > config.claimsMaxArea()) {
            return "§cClaim too large (max " + config.claimsMaxArea() + ").";
        }
        synchronized (local) {
            for (Claim c : local) {
                if (c.isSubdivision()) {
                    continue;
                }
                if (c.overlaps(world, minX, maxX, minZ, maxZ)) {
                    return "§cOverlaps existing claim #" + c.id();
                }
            }
        }
        int blocks = repo.getBlocks(player.getUniqueId(), config.claimsStartingBlocks());
        if (blocks < area) {
            return "§cNeed " + area + " claim blocks (you have " + blocks + ").";
        }
        Claim draft = Claim.topLevel(0, player.getUniqueId(), config.serverId(), world,
                minX, maxX, minZ, maxZ, player.getName() + "'s claim");
        long id = repo.create(draft);
        repo.setBlocks(player.getUniqueId(), blocks - area);
        Claim created = new Claim(id, draft.owner(), draft.serverId(), draft.world(),
                minX, maxX, minZ, maxZ, draft.name(), null, 0, false);
        synchronized (local) {
            local.add(created);
        }
        ClaimVisualizer.show(plugin, player, created, config.claimsVisualSeconds());
        modes.put(player.getUniqueId(), SelectMode.CLAIM);
        return "§aClaim §f#" + id + " §acreated (" + area + " blocks). Remaining: " + (blocks - area);
    }

    private String createSubdivision(Player player, String world, int minX, int maxX, int minZ, int maxZ, int area)
            throws SQLException {
        if (area < config.claimsSubMinArea()) {
            return "§cSubdivision too small (min " + config.claimsSubMinArea() + ").";
        }
        // parent must contain both corners — use center of rect
        Location mid = new Location(Bukkit.getWorld(world), (minX + maxX) / 2.0, 64, (minZ + maxZ) / 2.0);
        Optional<Claim> top = getTopLevelAt(mid);
        if (top.isEmpty() || !top.get().world().equals(world)) {
            modes.put(player.getUniqueId(), SelectMode.CLAIM);
            return "§cStand inside your claim to subdivide. Mode reset to claim.";
        }
        Claim parent = top.get();
        if (!parent.owner().equals(player.getUniqueId())
                && !hasTrust(parent, player.getUniqueId(), ClaimRepository.TrustLevel.MANAGE)
                && !player.hasPermission("yapdata.claims.admin")) {
            return "§cYou need manage trust on the parent claim.";
        }
        if (!parent.containsFully(minX, maxX, minZ, maxZ)) {
            return "§cSubdivision must be fully inside claim #" + parent.id();
        }
        synchronized (local) {
            for (Claim c : local) {
                if (!c.isSubdivision() || c.parentId() != parent.id()) {
                    continue;
                }
                if (c.overlaps(world, minX, maxX, minZ, maxZ)) {
                    return "§cOverlaps subdivision #" + c.id();
                }
            }
        }
        Claim draft = new Claim(0, parent.owner(), config.serverId(), world,
                minX, maxX, minZ, maxZ, "Sub of #" + parent.id(), parent.id(), 0, false);
        long id = repo.create(draft);
        Claim created = new Claim(id, draft.owner(), draft.serverId(), draft.world(),
                minX, maxX, minZ, maxZ, draft.name(), parent.id(), 0, false);
        synchronized (local) {
            local.add(created);
        }
        ClaimVisualizer.show(plugin, player, created, config.claimsVisualSeconds());
        modes.put(player.getUniqueId(), SelectMode.CLAIM);
        return "§aSubdivision §f#" + id + " §ainside claim §f#" + parent.id()
                + " §a(" + area + " blocks). Mode back to claim.";
    }

    public boolean abandon(Player player, Claim claim) throws SQLException {
        if (!claim.owner().equals(player.getUniqueId()) && !player.hasPermission("yapdata.claims.admin")) {
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
}
