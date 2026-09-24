package com.yapcore.yapblock.protect;

import com.yapcore.sched.YapSched;
import com.yapcore.yapblock.YapblockPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

final class IslandListenerMove implements Listener {

    private final YapblockPlugin plugin;

    IslandListenerMove(YapblockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        IslandAccess access = AccessLookup.access(plugin);
        if (access == null) {
            return;
        }
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        Location from = event.getFrom();
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        if (access.isIslandWorld(to) && to.getY() < access.voidY()) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            player.setFallDistance(0f);
            var service = plugin.service();
            if (service != null) {
                service.teleportHome(player).thenAccept(ok -> YapSched.entity(plugin, player, () -> {
                    if (Boolean.TRUE.equals(ok)) {
                        player.setFallDistance(0f);
                        player.sendMessage(Component.text(
                                "Teleported home (void fall).", NamedTextColor.YELLOW));
                    }
                }));
            }
            return;
        }

        if (sameIsland(access, from, to)) {
            return;
        }
        if (!access.canEnter(event.getPlayer(), to)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("You cannot enter this island.", NamedTextColor.RED));
        }
    }

    private static boolean sameIsland(IslandAccess access, Location from, Location to) {
        var a = access.islandAt(from).map(i -> i.id());
        var b = access.islandAt(to).map(i -> i.id());
        return a.equals(b);
    }
}
