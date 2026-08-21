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
 * In-memory claim index for this backend + block balances / selection state.
 */
public final class ClaimService {

    public record Corner(String world, int x, int z) {
    }

    private final JavaPlugin plugin;
    private final PlayerDataConfig config;
    private final ClaimRepository repo;

    private final List<Claim> local = new ArrayList<>();
    private final Map<UUID, Corner> pending = new ConcurrentHashMap<>();
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
            long period = 20L * 60L; // every minute
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

    public Optional<Claim> getAt(Location loc) {
        if (loc.getWorld() == null) {
            return Optional.empty();
        }
        String world = loc.getWorld().getName();
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        synchronized (local) {
            for (Claim c : local) {
                if (c.contains(world, x, z)) {
                    return Optional.of(c);
                }
            }
        }
        return Optional.empty();
    }

    public List<Claim> localClaims() {
        synchronized (local) {
            return List.copyOf(local);
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
        return hasTrust(claim.get(), player.getUniqueId(), ClaimRepository.TrustLevel.BUILD);
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
        return hasTrust(claim.get(), player.getUniqueId(), ClaimRepository.TrustLevel.ACCESS);
    }

    public boolean hasTrust(Claim claim, UUID player, ClaimRepository.TrustLevel needed) {
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

    public Corner pending(UUID uuid) {
        return pending.get(uuid);
    }

    public void clearPending(UUID uuid) {
        pending.remove(uuid);
    }

    /** First click stores corner; second creates claim. Returns status message. */
    public String handleShovel(Player player, Location loc) throws SQLException {
        if (!config.claimsEnabled()) {
            return "§cClaims are disabled.";
        }
        Corner first = pending.get(player.getUniqueId());
        if (first == null) {
            pending.put(player.getUniqueId(), new Corner(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockZ()));
            return "§aClaim corner #1 set. Click opposite corner with golden shovel.";
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

        if (area < config.claimsMinArea()) {
            return "§cClaim too small (min " + config.claimsMinArea() + " blocks).";
        }
        if (area > config.claimsMaxArea()) {
            return "§cClaim too large (max " + config.claimsMaxArea() + " blocks).";
        }
        synchronized (local) {
            for (Claim c : local) {
                if (c.overlaps(loc.getWorld().getName(), minX, maxX, minZ, maxZ)) {
                    return "§cOverlaps existing claim #" + c.id();
                }
            }
        }
        int blocks = repo.getBlocks(player.getUniqueId(), config.claimsStartingBlocks());
        if (blocks < area) {
            return "§cNeed " + area + " claim blocks (you have " + blocks + ").";
        }
        Claim draft = new Claim(0, player.getUniqueId(), config.serverId(), loc.getWorld().getName(),
                minX, maxX, minZ, maxZ, player.getName() + "'s claim");
        long id = repo.create(draft);
        repo.setBlocks(player.getUniqueId(), blocks - area);
        Claim created = new Claim(id, draft.owner(), draft.serverId(), draft.world(),
                minX, maxX, minZ, maxZ, draft.name());
        synchronized (local) {
            local.add(created);
        }
        ClaimVisualizer.show(plugin, player, created, config.claimsVisualSeconds());
        return "§aClaim §f#" + id + " §acreated (" + area + " blocks). Remaining: "
                + (blocks - area);
    }

    public boolean abandon(Player player, Claim claim) throws SQLException {
        if (!claim.owner().equals(player.getUniqueId()) && !player.hasPermission("yapdata.claims.admin")) {
            return false;
        }
        int refund = claim.area();
        if (!repo.delete(claim.id())) {
            return false;
        }
        synchronized (local) {
            local.removeIf(c -> c.id() == claim.id());
        }
        trustCache.remove(claim.id());
        if (claim.owner().equals(player.getUniqueId())) {
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
}
