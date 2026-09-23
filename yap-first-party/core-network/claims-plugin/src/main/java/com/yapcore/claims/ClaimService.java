package com.yapcore.claims;

import com.yapcore.claims.ClaimsConfig;
import com.yapcore.claims.db.ClaimRepository;
import com.yapcore.playerdata.PlayerDataServiceProvider;
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
import java.util.Set;
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
    private final ClaimsConfig config;
    private final ClaimRepository repo;
    private final ClaimFlagService flags;
    private final ClaimMessageRepository messages;

    private final List<Claim> local = new ArrayList<>();
    private final Map<UUID, Corner> pending = new ConcurrentHashMap<>();
    private final Map<UUID, SelectMode> modes = new ConcurrentHashMap<>();
    private final Map<Long, Map<UUID, ClaimRepository.TrustLevel>> trustCache = new ConcurrentHashMap<>();
    /** Players with continuous claim-border particles on. */
    private final Set<UUID> borderView = ConcurrentHashMap.newKeySet();
    private YapTask accrualTask;
    private YapTask borderTask;
    private final ClaimCreationOps creation;
    private final ClaimAccessOps access;
    private final ClaimBorderOps borders;

    public ClaimService(JavaPlugin plugin, ClaimsConfig config, ClaimRepository repo,
                        ClaimFlagService flags) {
        this(plugin, config, repo, flags, null);
    }

    public ClaimService(JavaPlugin plugin, ClaimsConfig config, ClaimRepository repo,
                        ClaimFlagService flags, ClaimMessageRepository messages) {
        this.plugin = plugin;
        this.config = config;
        this.repo = repo;
        this.flags = flags;
        this.messages = messages;
        this.creation = new ClaimCreationOps(this);
        this.access = new ClaimAccessOps(this);
        this.borders = new ClaimBorderOps(this);
    }

    public ClaimFlagService flags() {
        return flags;
    }

    public void start() {
        reloadLocal();
        if (config.claimsUseClaimBlocks() && config.claimsBlocksPerHour() > 0) {
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
        // Continuous border view — every 10 ticks for players who toggled /claim show
        borderTask = YapSched.globalTimer(plugin, borders::tickBorderView, 10L, 10L);
    }

    public void stop() {
        if (accrualTask != null) {
            accrualTask.cancel();
            accrualTask = null;
        }
        if (borderTask != null) {
            borderTask.cancel();
            borderTask = null;
        }
        borderView.clear();
        local.clear();
        pending.clear();
        modes.clear();
        trustCache.clear();
        flags.invalidateAll();
        if (messages != null) {
            messages.invalidateAll();
        }
    }

    /** Toggle continuous particle borders for owned claims. Returns new on/off state. */
    public boolean toggleBorderView(UUID uuid) {
        return borders.toggleBorderView(uuid);
    }

    public boolean borderViewEnabled(UUID uuid) {
        return borders.borderViewEnabled(uuid);
    }

    public void clearBorderView(UUID uuid) {
        borders.clearBorderView(uuid);
    }

    /** Owned claims on this world whose footprint is within {@code radius} blocks of the player. */
    public List<Claim> ownedNear(Player player, int radius) {
        return borders.ownedNear(player, radius);
    }

    public ClaimMessageRepository messages() {
        return messages;
    }

    public java.util.Optional<String> message(long claimId, com.yapcore.regions.RegionMessageKind kind) {
        if (messages == null) {
            return java.util.Optional.empty();
        }
        return messages.get(claimId, kind);
    }

    public void setMessage(long claimId, com.yapcore.regions.RegionMessageKind kind, String text)
            throws SQLException {
        if (messages == null) {
            throw new SQLException("Claim messages unavailable");
        }
        messages.set(claimId, kind, text);
    }

    public void clearMessage(long claimId, com.yapcore.regions.RegionMessageKind kind) throws SQLException {
        if (messages == null) {
            throw new SQLException("Claim messages unavailable");
        }
        messages.clear(claimId, kind);
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
        return getAt(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), true);
    }

    /** Top-level claim covering XZ (ignores Y — used for subdivision parent lookup). */
    public Optional<Claim> getTopLevelAtXZ(String world, int x, int z) {
        Claim best = null;
        synchronized (local) {
            for (Claim c : local) {
                if (c.isSubdivision() || !c.contains(world, x, z)) {
                    continue;
                }
                if (best == null || c.area() < best.area()) {
                    best = c;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private Optional<Claim> getAt(String world, int x, int y, int z, boolean checkY) {
        Claim best = null;
        synchronized (local) {
            for (Claim c : local) {
                if (checkY) {
                    if (!c.contains(world, x, y, z)) {
                        continue;
                    }
                } else if (!c.contains(world, x, z)) {
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
        return access.canBuild(player, loc);
    }

    public boolean canAccess(Player player, Location loc) {
        return access.canAccess(player, loc);
    }

    public boolean canEnter(Player player, Location loc) {
        return access.canEnter(player, loc);
    }

    public boolean isPvpAllowed(Player attacker, Player victim) {
        return access.isPvpAllowed(attacker, victim);
    }

    public boolean isDamageAllowed(Location loc) {
        return access.isDamageAllowed(loc);
    }

    public boolean isMobDamageAllowed(org.bukkit.entity.Player victim) {
        return access.isMobDamageAllowed(victim);
    }

    public boolean isFireSpreadAllowed(Location loc) {
        return access.isFireSpreadAllowed(loc);
    }

    public boolean isMobSpawningAllowed(Location loc) {
        return access.isMobSpawningAllowed(loc);
    }

    public boolean isMobEntryAllowed(Location loc) {
        return access.isMobEntryAllowed(loc);
    }

    public boolean forcesClearWeather(Location loc) {
        return access.forcesClearWeather(loc);
    }

    public boolean canDropItems(Player player, Location loc) {
        return access.canDropItems(player, loc);
    }

    public boolean canPickupItems(Player player, Location loc) {
        return access.canPickupItems(player, loc);
    }

    public boolean isTntAllowed(Location loc) {
        return access.isTntAllowed(loc);
    }

    public boolean isCreeperExplosionAllowed(Location loc) {
        return access.isCreeperExplosionAllowed(loc);
    }

    public boolean canOpenContainer(Player player, Location loc) {
        return access.canOpenContainer(player, loc);
    }

    public boolean canUse(Player player, Location loc) {
        return access.canUse(player, loc);
    }

    public boolean canUseNetherPortal(Player player, Location loc) {
        return access.canUseNetherPortal(player, loc);
    }

    /** Used by YaPFactions upkeep when a linked claim cannot pay. */
    public void setTaxFrozen(long claimId, boolean frozen) {
        for (Claim claim : local) {
            if (claim.id() == claimId) {
                claim.setTaxFrozen(frozen);
                try {
                    repo.setTax(claimId, claim.taxDue(), frozen);
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "setTaxFrozen " + claimId, e);
                }
                return;
            }
        }
        try {
            Optional<Claim> loaded = repo.get(claimId);
            if (loaded.isPresent()) {
                Claim claim = loaded.get();
                repo.setTax(claimId, claim.taxDue(), frozen);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "setTaxFrozen " + claimId, e);
        }
    }

    public boolean hasTrust(Claim claim, UUID player, ClaimRepository.TrustLevel needed) {
        return access.hasTrust(claim, player, needed);
    }

    public void invalidateTrust(long claimId) {
        trustCache.remove(claimId);
    }

    public String handleShovel(Player player, Location loc) throws SQLException {
        if (!config.claimsEnabled()) {
            return "§cClaims are disabled.";
        }
        SelectMode mode = mode(player.getUniqueId());
        // Chunk mode: one click claims the Minecraft chunk (unless subdividing).
        if (mode != SelectMode.SUBDIVIDE
                && config.claimsMode() == ClaimsConfig.ClaimMode.CHUNK) {
            return claimChunkAt(player, loc);
        }
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

    /** Claim the aligned plot containing {@code loc} (default 64×64, depth below, to max height). */
    public String claimChunkAt(Player player, Location loc) throws SQLException {
        if (loc == null || loc.getWorld() == null) {
            return "§cInvalid location.";
        }
        int size = Math.max(1, config.claimsPlotSize());
        ClaimExpandRules.Plot plot = ClaimExpandRules.plotAt(loc.getBlockX(), loc.getBlockZ(), size);
        int maxY = loc.getWorld().getMaxHeight() - 1;
        int minY = Math.max(loc.getWorld().getMinHeight(), loc.getBlockY() - config.claimsPlotDepth());
        int area = size * size;
        pending.remove(player.getUniqueId());
        return creation.createTopLevel(player, loc.getWorld().getName(),
                plot.minX(), plot.maxX(), plot.minZ(), plot.maxZ(), minY, maxY, area);
    }

    /**
     * Claim the next grid plot in {@code dir} (or facing yaw). Must share an edge with land you own.
     * Copies Y bounds from the touching owned claim so basements stay covered.
     */
    public String expandAdjacent(Player player, ClaimExpandRules.Dir dir) throws SQLException {
        if (player == null || player.getWorld() == null) {
            return "§cInvalid location.";
        }
        if (config.claimsMode() != ClaimsConfig.ClaimMode.CHUNK) {
            return "§c/claim expand needs chunk/plot mode (claims.mode: chunk).";
        }
        int size = Math.max(1, config.claimsPlotSize());
        Location feet = player.getLocation();
        ClaimExpandRules.Plot here = ClaimExpandRules.plotAt(feet.getBlockX(), feet.getBlockZ(), size);
        ClaimExpandRules.Plot target = ClaimExpandRules.adjacent(here, dir, size);

        Claim neighbor = null;
        synchronized (local) {
            for (Claim c : local) {
                if (c.isSubdivision() || !c.owner().equals(player.getUniqueId())) {
                    continue;
                }
                if (!c.world().equals(feet.getWorld().getName())) {
                    continue;
                }
                if (ClaimExpandRules.sharesEdgeOrOverlaps(
                        c.minX(), c.maxX(), c.minZ(), c.maxZ(),
                        target.minX(), target.maxX(), target.minZ(), target.maxZ())) {
                    neighbor = c;
                    break;
                }
            }
        }
        if (neighbor == null) {
            return "§cNo owned claim touches the plot to the §f" + dir.label()
                    + "§c. Stand in / next to your claim, face the empty plot, then §f/claim expand§c.";
        }

        // Already claimed (by anyone) — check XZ at mid-height of neighbor claim
        int probeY = Math.max(neighbor.minY(), Math.min(neighbor.maxY(), feet.getBlockY()));
        Location probe = new Location(feet.getWorld(),
                target.centerX() + 0.5, probeY, target.centerZ() + 0.5);
        Optional<Claim> existing = getAt(probe);
        if (existing.isPresent()) {
            Claim e = existing.get();
            if (e.owner().equals(player.getUniqueId())) {
                return "§eYou already own that plot (§f#" + e.id() + "§e).";
            }
            return "§cThat plot is already claimed (§f#" + e.id() + "§c).";
        }

        int area = size * size;
        pending.remove(player.getUniqueId());
        String msg = creation.createTopLevel(player, feet.getWorld().getName(),
                target.minX(), target.maxX(), target.minZ(), target.maxZ(),
                neighbor.minY(), neighbor.maxY(), area);
        if (msg.startsWith("§aClaim")) {
            return "§aExpanded §f" + dir.label() + "§a — " + msg.substring(2);
        }
        return msg;
    }

    public boolean abandon(Player player, Claim claim) throws SQLException {
        if (!claim.owner().equals(player.getUniqueId()) && !StaffBypass.land(player)) {
            return false;
        }
        int refundBlocks = (!claim.isSubdivision() && config.claimsUseClaimBlocks()) ? claim.area() : 0;
        final double refundMoney;
        if (!claim.isSubdivision() && config.claimsCostRefundOnAbandon()) {
            refundMoney = Math.round(config.claimsCostAmount()
                    * (config.claimsCostRefundPercent() / 100.0) * 100.0) / 100.0;
        } else {
            refundMoney = 0;
        }
        if (!repo.delete(claim.id())) {
            return false;
        }
        synchronized (local) {
            local.removeIf(c -> c.id() == claim.id()
                    || (c.parentId() != null && c.parentId() == claim.id()));
        }
        trustCache.remove(claim.id());
        if (refundBlocks > 0 && claim.owner().equals(player.getUniqueId())) {
            int cur = repo.getBlocks(player.getUniqueId(), config.claimsStartingBlocks());
            repo.setBlocks(player.getUniqueId(), cur + refundBlocks);
        }
        if (refundMoney > 0 && claim.owner().equals(player.getUniqueId())) {
            PlayerDataServiceProvider.find().ifPresent(eco ->
                    eco.deposit(player.getUniqueId(), refundMoney));
        }
        return true;
    }

    public ClaimRepository repo() {
        return repo;
    }

    public ClaimsConfig config() {
        return config;
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    List<Claim> localClaimsMutable() {
        return local;
    }

    Set<UUID> borderViewMutable() {
        return borderView;
    }

    java.util.Map<UUID, SelectMode> modesMutable() {
        return modes;
    }

    Map<Long, Map<UUID, ClaimRepository.TrustLevel>> trustCacheMutable() {
        return trustCache;
    }
}
