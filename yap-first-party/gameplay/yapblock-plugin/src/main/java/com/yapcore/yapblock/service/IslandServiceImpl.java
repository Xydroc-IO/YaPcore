package com.yapcore.yapblock.service;

import com.yapcore.yapblock.IslandRole;
import com.yapcore.yapblock.IslandService;
import com.yapcore.yapblock.IslandSnapshot;
import com.yapcore.yapblock.YapblockConfig;
import com.yapcore.yapblock.db.IslandRepository;
import com.yapcore.yapblock.db.MemberRepository;
import com.yapcore.yapblock.gen.SchematicIslandPaster;
import com.yapcore.yapblock.grid.GridAllocator;
import com.yapcore.yapblock.grid.IslandGrid;
import com.yapcore.yapblock.grid.IslandIndex;
import com.yapcore.yapblock.level.IslandLevelScanner;
import com.yapcore.yapblock.level.IslandTopCache;
import com.yapcore.yapblock.protect.IslandAccess;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class IslandServiceImpl implements IslandService {

    private final JavaPlugin plugin;
    private final YapblockConfig config;
    private final IslandIndex index;
    private final IslandRoleCache roles;
    private final IslandAccess access;
    private final IslandCreateOps createOps;
    private final IslandDeleteOps deleteOps;
    private final IslandUpgradeOps upgradeOps;
    private final IslandVisitOps visitOps;
    private final IslandMemberOps memberOps;
    private final IslandSettingsOps settingsOps;
    private final IslandLevelScanner levelScanner;
    private final IslandTopCache topCache;
    private final IslandRepository islands;
    private final MemberRepository members;

    public IslandServiceImpl(
            JavaPlugin plugin,
            YapblockConfig config,
            IslandGrid grid,
            GridAllocator allocator,
            IslandIndex index,
            IslandRepository islands,
            MemberRepository members,
            IslandRoleCache roles,
            SchematicIslandPaster paster,
            IslandLevelScanner levelScanner,
            IslandTopCache topCache) {
        this.plugin = plugin;
        this.config = config;
        this.index = index;
        this.roles = roles;
        this.islands = islands;
        this.members = members;
        this.levelScanner = levelScanner;
        this.topCache = topCache;
        this.access = new IslandAccess(config, index, roles);
        this.createOps = new IslandCreateOps(plugin, config, grid, allocator, index, islands, members, roles, paster);
        this.deleteOps = new IslandDeleteOps(
                plugin, config, grid, index, allocator, islands, members, roles, paster);
        this.upgradeOps = new IslandUpgradeOps(plugin, config, index, islands, roles);
        this.visitOps = new IslandVisitOps(plugin, config, index, islands, roles);
        this.memberOps = new IslandMemberOps(plugin, config, index, members, roles, visitOps);
        this.settingsOps = new IslandSettingsOps(plugin, index, islands, roles);
    }

    public void loadFromDatabase(GridAllocator allocator) {
        try {
            index.clear();
            roles.clear();
            for (IslandSnapshot snap : islands.loadAll()) {
                index.put(snap);
                allocator.markOccupied(snap.gridX(), snap.gridZ());
            }
            for (MemberRepository.MemberRow row : members.loadAll()) {
                roles.put(row.islandId(), row.playerId(), row.role());
                if (row.role() == IslandRole.OWNER || row.role() == IslandRole.MEMBER) {
                    index.bindMember(row.playerId(), row.islandId());
                }
            }
            topCache.invalidate();
            plugin.getLogger().info("Loaded " + index.all().size() + " islands");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed loading islands", e);
        }
    }

    public IslandAccess access() {
        return access;
    }

    public IslandCreateOps createOps() {
        return createOps;
    }

    public IslandDeleteOps deleteOps() {
        return deleteOps;
    }

    public IslandUpgradeOps upgradeOps() {
        return upgradeOps;
    }

    public IslandVisitOps visitOps() {
        return visitOps;
    }

    public IslandMemberOps memberOps() {
        return memberOps;
    }

    public IslandSettingsOps settingsOps() {
        return settingsOps;
    }

    public IslandLevelScanner levelScanner() {
        return levelScanner;
    }

    public IslandTopCache topCache() {
        return topCache;
    }

    public IslandIndex index() {
        return index;
    }

    public IslandRoleCache roles() {
        return roles;
    }

    public YapblockConfig config() {
        return config;
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public World islandWorld() {
        return Bukkit.getWorld(config.worldName());
    }

    @Override
    public boolean enabled() {
        return config.enabled();
    }

    @Override
    public Optional<IslandSnapshot> islandById(long islandId) {
        return index.byId(islandId);
    }

    @Override
    public Optional<IslandSnapshot> islandAt(Location location) {
        return index.at(location);
    }

    @Override
    public Optional<IslandSnapshot> islandOf(UUID playerId) {
        return index.ofPlayer(playerId);
    }

    @Override
    public Optional<IslandRole> role(UUID playerId, long islandId) {
        return roles.role(playerId, islandId);
    }

    @Override
    public boolean canBuild(Player player, Location location) {
        return access.canBuild(player, location);
    }

    @Override
    public boolean canEnter(Player player, Location location) {
        return access.canEnter(player, location);
    }

    @Override
    public CompletableFuture<Optional<IslandSnapshot>> createIsland(Player player) {
        return createOps.create(player);
    }

    @Override
    public CompletableFuture<Boolean> teleportHome(Player player) {
        return visitOps.teleportHome(player);
    }

    @Override
    public CompletableFuture<Boolean> arriveOrCreate(Player player) {
        if (index.ofPlayer(player.getUniqueId()).isPresent()) {
            return visitOps.teleportHome(player);
        }
        return createOps.create(player).thenCompose(snap -> {
            if (snap.isEmpty()) {
                return CompletableFuture.completedFuture(false);
            }
            // create() already teleports after paste; treat success as arrived
            return CompletableFuture.completedFuture(true);
        });
    }

    @Override
    public List<IslandSnapshot> topIslands(int limit) {
        return topCache.top(limit);
    }

    public CompletableFuture<Boolean> setLevel(long islandId, long level) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        try {
            islands.updateLevel(islandId, level);
            index.byId(islandId).ifPresent(snap -> {
                index.put(snap.withLevel(level));
                topCache.invalidate();
            });
            future.complete(true);
        } catch (Exception e) {
            future.complete(false);
        }
        return future;
    }
}
