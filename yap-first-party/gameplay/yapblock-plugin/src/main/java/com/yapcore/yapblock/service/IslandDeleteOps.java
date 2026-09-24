package com.yapcore.yapblock.service;

import com.yapcore.sched.YapSched;
import com.yapcore.yapblock.IslandRole;
import com.yapcore.yapblock.IslandSnapshot;
import com.yapcore.yapblock.YapblockConfig;
import com.yapcore.yapblock.db.IslandRepository;
import com.yapcore.yapblock.db.MemberRepository;
import com.yapcore.yapblock.gen.SchematicIslandPaster;
import com.yapcore.yapblock.grid.GridAllocator;
import com.yapcore.yapblock.grid.IslandGrid;
import com.yapcore.yapblock.grid.IslandIndex;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class IslandDeleteOps {

    private enum PendingKind { DELETE, RESET }

    private final JavaPlugin plugin;
    private final YapblockConfig config;
    private final IslandGrid grid;
    private final IslandIndex index;
    private final GridAllocator allocator;
    private final IslandRepository islands;
    private final MemberRepository members;
    private final IslandRoleCache roles;
    private final SchematicIslandPaster paster;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public IslandDeleteOps(
            JavaPlugin plugin,
            YapblockConfig config,
            IslandGrid grid,
            IslandIndex index,
            GridAllocator allocator,
            IslandRepository islands,
            MemberRepository members,
            IslandRoleCache roles,
            SchematicIslandPaster paster) {
        this.plugin = plugin;
        this.config = config;
        this.grid = grid;
        this.index = index;
        this.allocator = allocator;
        this.islands = islands;
        this.members = members;
        this.roles = roles;
        this.paster = paster;
    }

    public void requestDelete(Player player) {
        IslandSnapshot snap = requireOwner(player);
        if (snap == null) {
            return;
        }
        pending.put(player.getUniqueId(), new Pending(PendingKind.DELETE, snap.id()));
        player.sendMessage(Component.text(
                "Type /is confirm within 30s to permanently delete your island.", NamedTextColor.YELLOW));
        scheduleExpire(player.getUniqueId(), snap.id(), PendingKind.DELETE);
    }

    public void requestReset(Player player) {
        IslandSnapshot snap = requireOwner(player);
        if (snap == null) {
            return;
        }
        pending.put(player.getUniqueId(), new Pending(PendingKind.RESET, snap.id()));
        player.sendMessage(Component.text(
                "Type /is confirm within 30s to reset your island (keeps ownership).", NamedTextColor.YELLOW));
        scheduleExpire(player.getUniqueId(), snap.id(), PendingKind.RESET);
    }

    public CompletableFuture<Boolean> confirmDelete(Player player) {
        Pending p = pending.remove(player.getUniqueId());
        if (p == null) {
            player.sendMessage(Component.text(
                    "No pending action. Use /is delete or /is reset first.", NamedTextColor.RED));
            return CompletableFuture.completedFuture(false);
        }
        if (p.kind() == PendingKind.RESET) {
            return resetIsland(p.islandId(), player);
        }
        return disband(p.islandId(), player);
    }

    public CompletableFuture<Boolean> disband(long islandId, Player notifier) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        YapSched.async(plugin, () -> {
            try {
                IslandSnapshot snap = index.byId(islandId).orElse(null);
                if (snap == null) {
                    snap = islands.findById(islandId).orElse(null);
                }
                if (snap == null) {
                    if (notifier != null) {
                        YapSched.entity(plugin, notifier, () ->
                                notifier.sendMessage(Component.text("Island not found.", NamedTextColor.RED)));
                    }
                    future.complete(false);
                    return;
                }
                islands.delete(islandId);
                allocator.release(snap.gridX(), snap.gridZ());
                roles.clearIsland(islandId);
                index.remove(islandId);
                if (notifier != null) {
                    YapSched.entity(plugin, notifier, () ->
                            notifier.sendMessage(Component.text("Island deleted.", NamedTextColor.GREEN)));
                }
                Player onlineOwner = Bukkit.getPlayer(snap.ownerId());
                if (onlineOwner != null && (notifier == null || !onlineOwner.equals(notifier))) {
                    YapSched.entity(plugin, onlineOwner, () ->
                            onlineOwner.sendMessage(Component.text(
                                    "Your island was disbanded.", NamedTextColor.RED)));
                }
                future.complete(true);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Island delete failed", e);
                future.complete(false);
            }
        });
        return future;
    }

    private CompletableFuture<Boolean> resetIsland(long islandId, Player player) {
        IslandSnapshot snap = index.byId(islandId).orElse(null);
        if (snap == null) {
            player.sendMessage(Component.text("Island not found.", NamedTextColor.RED));
            return CompletableFuture.completedFuture(false);
        }
        World world = Bukkit.getWorld(config.worldName());
        if (world == null) {
            player.sendMessage(Component.text("Island world not loaded.", NamedTextColor.RED));
            return CompletableFuture.completedFuture(false);
        }
        int cx = grid.worldX(snap.gridX());
        int cz = grid.worldZ(snap.gridZ());
        int r = snap.sizeRadius();
        int pasteY = grid.pasteY();
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        player.sendMessage(Component.text("Resetting island…", NamedTextColor.GRAY));
        YapSched.region(plugin, world, cx, cz, () -> {
            try {
                int minY = Math.max(world.getMinHeight(), pasteY - 32);
                int maxY = Math.min(world.getMaxHeight(), pasteY + 64);
                for (int x = cx - r; x <= cx + r; x++) {
                    for (int z = cz - r; z <= cz + r; z++) {
                        double dx = x - cx;
                        double dz = z - cz;
                        if ((dx * dx + dz * dz) > (double) r * r) {
                            continue;
                        }
                        for (int y = minY; y < maxY; y++) {
                            world.getBlockAt(x, y, z).setType(Material.AIR, false);
                        }
                    }
                }
                paster.pasteIsland(world, cx, pasteY, cz).whenComplete((v, err) -> {
                    if (err != null) {
                        plugin.getLogger().log(Level.WARNING, "Island reset paste failed", err);
                        YapSched.entity(plugin, player, () ->
                                player.sendMessage(Component.text("Reset paste failed.", NamedTextColor.RED)));
                        future.complete(false);
                        return;
                    }
                    YapSched.async(plugin, () -> {
                        try {
                            islands.updateHome(islandId, cx + 0.5, pasteY + 1.0, cz + 0.5);
                            IslandSnapshot updated = snap.withHome(cx + 0.5, pasteY + 1.0, cz + 0.5).withLevel(0L);
                            islands.updateLevel(islandId, 0L);
                            index.put(updated);
                            YapSched.entity(plugin, player, () -> {
                                player.sendMessage(Component.text("Island reset.", NamedTextColor.GREEN));
                                LocationTeleport.teleport(plugin, player,
                                        index.homeLocation(world, updated), ok -> {
                                        });
                            });
                            future.complete(true);
                        } catch (Exception e) {
                            future.complete(false);
                        }
                    });
                });
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Island reset failed", e);
                future.complete(false);
            }
        });
        return future;
    }

    private void scheduleExpire(UUID playerId, long islandId, PendingKind kind) {
        YapSched.asyncLater(plugin, () -> {
            Pending cur = pending.get(playerId);
            if (cur != null && cur.islandId() == islandId && cur.kind() == kind) {
                pending.remove(playerId, cur);
            }
        }, 20L * 30);
    }

    private IslandSnapshot requireOwner(Player player) {
        IslandSnapshot snap = index.ofPlayer(player.getUniqueId()).orElse(null);
        if (snap == null) {
            player.sendMessage(Component.text("You have no island.", NamedTextColor.RED));
            return null;
        }
        IslandRole role = roles.role(player.getUniqueId(), snap.id()).orElse(null);
        if (role != IslandRole.OWNER) {
            player.sendMessage(Component.text("Only the owner can do that.", NamedTextColor.RED));
            return null;
        }
        return snap;
    }

    private record Pending(PendingKind kind, long islandId) {
    }
}
