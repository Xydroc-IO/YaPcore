package com.yapcore.yapblock.protect;

import com.yapcore.yapblock.IslandFlag;
import com.yapcore.yapblock.IslandRole;
import com.yapcore.yapblock.IslandSnapshot;
import com.yapcore.yapblock.YapblockConfig;
import com.yapcore.yapblock.grid.IslandIndex;
import com.yapcore.yapblock.service.IslandRoleCache;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/** Shared access checks for protection listeners and commands. */
public final class IslandAccess {

    private final YapblockConfig config;
    private final IslandIndex index;
    private final IslandRoleCache roles;

    public IslandAccess(YapblockConfig config, IslandIndex index, IslandRoleCache roles) {
        this.config = config;
        this.index = index;
        this.roles = roles;
    }

    public boolean bypass(Player player) {
        return player != null && player.hasPermission("yapblock.admin.bypass");
    }

    public Optional<IslandSnapshot> islandAt(Location location) {
        return index.at(location);
    }

    public boolean canBuild(Player player, Location location) {
        if (player == null || location == null) {
            return false;
        }
        if (bypass(player)) {
            return true;
        }
        Optional<IslandSnapshot> island = index.at(location);
        if (island.isEmpty()) {
            return !isIslandWorld(location);
        }
        IslandSnapshot snap = island.get();
        Optional<IslandRole> role = roles.role(player.getUniqueId(), snap.id());
        if (snap.flag(IslandFlag.LOCK)) {
            return role.isPresent() && (role.get() == IslandRole.OWNER || role.get() == IslandRole.MEMBER);
        }
        return role.map(IslandRole::canBuild).orElse(false);
    }

    public boolean canEnter(Player player, Location location) {
        if (player == null || location == null) {
            return true;
        }
        if (bypass(player)) {
            return true;
        }
        Optional<IslandSnapshot> island = index.at(location);
        if (island.isEmpty()) {
            return true;
        }
        IslandSnapshot snap = island.get();
        UUID id = player.getUniqueId();
        Optional<IslandRole> role = roles.role(id, snap.id());
        if (role.isPresent() && role.get().isBanned()) {
            return false;
        }
        if (role.isPresent()) {
            return true;
        }
        if (snap.flag(IslandFlag.LOCK)) {
            return false;
        }
        return snap.flag(IslandFlag.PUBLIC_VISIT);
    }

    public boolean pvpAllowed(Location location) {
        return index.at(location).map(s -> s.flag(IslandFlag.PVP)).orElse(true);
    }

    public boolean mobSpawnAllowed(Location location) {
        return index.at(location).map(s -> s.flag(IslandFlag.MOB_SPAWN)).orElse(true);
    }

    public boolean fireAllowed(Location location) {
        return index.at(location).map(s -> s.flag(IslandFlag.FIRE)).orElse(true);
    }

    public boolean isIslandWorld(Location location) {
        return location.getWorld() != null
                && location.getWorld().getName().equalsIgnoreCase(config.worldName());
    }

    public int voidY() {
        return config.voidY();
    }
}
