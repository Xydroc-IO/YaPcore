package com.yapcore.yapblock.service;

import com.yapcore.sched.YapSched;
import com.yapcore.yapblock.IslandFlag;
import com.yapcore.yapblock.IslandRole;
import com.yapcore.yapblock.IslandSnapshot;
import com.yapcore.yapblock.db.IslandRepository;
import com.yapcore.yapblock.grid.IslandIndex;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class IslandSettingsOps {

    private final JavaPlugin plugin;
    private final IslandIndex index;
    private final IslandRepository islands;
    private final IslandRoleCache roles;

    public IslandSettingsOps(
            JavaPlugin plugin,
            IslandIndex index,
            IslandRepository islands,
            IslandRoleCache roles) {
        this.plugin = plugin;
        this.index = index;
        this.islands = islands;
        this.roles = roles;
    }

    public IslandSnapshot requireManageable(Player player) {
        IslandSnapshot snap = index.ofPlayer(player.getUniqueId()).orElse(null);
        if (snap == null) {
            player.sendMessage(Component.text("You have no island.", NamedTextColor.RED));
            return null;
        }
        if (roles.role(player.getUniqueId(), snap.id()).orElse(null) != IslandRole.OWNER) {
            player.sendMessage(Component.text("Only the owner can change settings.", NamedTextColor.RED));
            return null;
        }
        return snap;
    }

    public CompletableFuture<Boolean> setFlag(Player player, IslandFlag flag, boolean value) {
        IslandSnapshot snap = requireManageable(player);
        if (snap == null) {
            return CompletableFuture.completedFuture(false);
        }
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        YapSched.async(plugin, () -> {
            try {
                islands.setFlag(snap.id(), flag, value);
                IslandSnapshot updated = snap.withFlag(flag, value);
                index.put(updated);
                YapSched.entity(plugin, player, () ->
                        player.sendMessage(Component.text(
                                flag.name() + " = " + value, NamedTextColor.GREEN)));
                future.complete(true);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "setFlag failed", e);
                future.complete(false);
            }
        });
        return future;
    }

    public CompletableFuture<Boolean> toggleFlag(Player player, IslandFlag flag) {
        IslandSnapshot snap = requireManageable(player);
        if (snap == null) {
            return CompletableFuture.completedFuture(false);
        }
        return setFlag(player, flag, !snap.flag(flag));
    }
}
