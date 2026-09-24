package com.yapcore.yapblock.service;

import com.yapcore.sched.YapSched;
import com.yapcore.yapblock.IslandFlag;
import com.yapcore.yapblock.IslandRole;
import com.yapcore.yapblock.IslandSnapshot;
import com.yapcore.yapblock.YapblockConfig;
import com.yapcore.yapblock.db.IslandRepository;
import com.yapcore.yapblock.grid.IslandIndex;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class IslandVisitOps {

    private final JavaPlugin plugin;
    private final YapblockConfig config;
    private final IslandIndex index;
    private final IslandRepository islands;
    private final IslandRoleCache roles;

    public IslandVisitOps(
            JavaPlugin plugin,
            YapblockConfig config,
            IslandIndex index,
            IslandRepository islands,
            IslandRoleCache roles) {
        this.plugin = plugin;
        this.config = config;
        this.index = index;
        this.islands = islands;
        this.roles = roles;
    }

    public CompletableFuture<Boolean> teleportHome(Player player) {
        IslandSnapshot snap = index.ofPlayer(player.getUniqueId()).orElse(null);
        if (snap == null) {
            player.sendMessage(Component.text("You have no island. Use /is create.", NamedTextColor.RED));
            return CompletableFuture.completedFuture(false);
        }
        return teleportTo(player, snap);
    }

    public CompletableFuture<Boolean> setHome(Player player) {
        IslandSnapshot snap = index.ofPlayer(player.getUniqueId()).orElse(null);
        if (snap == null) {
            player.sendMessage(Component.text("You have no island.", NamedTextColor.RED));
            return CompletableFuture.completedFuture(false);
        }
        IslandRole role = roles.role(player.getUniqueId(), snap.id()).orElse(null);
        if (role != IslandRole.OWNER && role != IslandRole.MEMBER) {
            player.sendMessage(Component.text("Only owners/members can set home.", NamedTextColor.RED));
            return CompletableFuture.completedFuture(false);
        }
        Location loc = player.getLocation();
        if (!index.at(loc).map(i -> i.id() == snap.id()).orElse(false)) {
            player.sendMessage(Component.text("You must be on your island.", NamedTextColor.RED));
            return CompletableFuture.completedFuture(false);
        }
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        YapSched.async(plugin, () -> {
            try {
                islands.updateHome(snap.id(), loc.getX(), loc.getY(), loc.getZ());
                index.put(snap.withHome(loc.getX(), loc.getY(), loc.getZ()));
                YapSched.entity(plugin, player, () ->
                        player.sendMessage(Component.text("Island home set.", NamedTextColor.GREEN)));
                future.complete(true);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "setHome failed", e);
                future.complete(false);
            }
        });
        return future;
    }

    public CompletableFuture<Boolean> visit(Player visitor, String targetName) {
        if (!visitor.hasPermission("yapblock.visit")) {
            visitor.sendMessage(Component.text("Missing permission yapblock.visit.", NamedTextColor.RED));
            return CompletableFuture.completedFuture(false);
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (target.getUniqueId() == null) {
            visitor.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return CompletableFuture.completedFuture(false);
        }
        IslandSnapshot snap = index.ofPlayer(target.getUniqueId()).orElse(null);
        if (snap == null) {
            visitor.sendMessage(Component.text("That player has no island.", NamedTextColor.RED));
            return CompletableFuture.completedFuture(false);
        }
        IslandRole role = roles.role(visitor.getUniqueId(), snap.id()).orElse(null);
        if (role == IslandRole.BANNED) {
            visitor.sendMessage(Component.text("You are banned from that island.", NamedTextColor.RED));
            return CompletableFuture.completedFuture(false);
        }
        if (role == null && (snap.flag(IslandFlag.LOCK) || !snap.flag(IslandFlag.PUBLIC_VISIT))) {
            visitor.sendMessage(Component.text("That island is not open to visitors.", NamedTextColor.RED));
            return CompletableFuture.completedFuture(false);
        }
        return teleportTo(visitor, snap);
    }

    private CompletableFuture<Boolean> teleportTo(Player player, IslandSnapshot snap) {
        World world = Bukkit.getWorld(config.worldName());
        if (world == null) {
            player.sendMessage(Component.text("Skyblock world is not loaded.", NamedTextColor.RED));
            return CompletableFuture.completedFuture(false);
        }
        Location dest = index.homeLocation(world, snap);
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        LocationTeleport.teleport(plugin, player, dest, ok -> {
            if (Boolean.TRUE.equals(ok)) {
                player.sendMessage(Component.text("Teleported to island.", NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("Teleport failed.", NamedTextColor.RED));
            }
            future.complete(Boolean.TRUE.equals(ok));
        });
        return future;
    }
}
