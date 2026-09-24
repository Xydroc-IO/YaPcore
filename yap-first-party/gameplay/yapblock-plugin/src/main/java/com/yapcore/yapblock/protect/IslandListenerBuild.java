package com.yapcore.yapblock.protect;

import com.yapcore.yapblock.YapblockPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;

final class IslandListenerBuild implements Listener {

    private final YapblockPlugin plugin;

    IslandListenerBuild(YapblockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        denyBuild(event.getPlayer(), event.getBlock().getLocation(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        denyBuild(event.getPlayer(), event.getBlock().getLocation(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        IslandAccess access = AccessLookup.access(plugin);
        if (access == null) {
            return;
        }
        if (!access.canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            denyMsg(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        IslandAccess access = AccessLookup.access(plugin);
        if (access == null) {
            return;
        }
        if (!access.canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            denyMsg(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        IslandAccess access = AccessLookup.access(plugin);
        if (access == null) {
            return;
        }
        if (!access.canBuild(player, event.getBlock().getLocation())) {
            event.setCancelled(true);
            denyMsg(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        IslandAccess access = AccessLookup.access(plugin);
        if (access == null) {
            return;
        }
        Player player = event.getPlayer();
        if (player != null && !access.canBuild(player, event.getBlock().getLocation())) {
            event.setCancelled(true);
            denyMsg(player);
            return;
        }
        if (player == null && !access.fireAllowed(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        IslandAccess access = AccessLookup.access(plugin);
        if (access == null) {
            return;
        }
        Player player = event.getPlayer();
        if (player != null && !access.canBuild(player, event.getEntity().getLocation())) {
            event.setCancelled(true);
            denyMsg(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        IslandAccess access = AccessLookup.access(plugin);
        if (access == null) {
            return;
        }
        if (event.getRemover() instanceof Player player
                && !access.canBuild(player, event.getEntity().getLocation())) {
            event.setCancelled(true);
            denyMsg(player);
        }
    }

    private void denyBuild(Player player, org.bukkit.Location loc, org.bukkit.event.Cancellable event) {
        IslandAccess access = AccessLookup.access(plugin);
        if (access == null) {
            return;
        }
        if (!access.canBuild(player, loc)) {
            event.setCancelled(true);
            denyMsg(player);
        }
    }

    private static void denyMsg(Player player) {
        player.sendMessage(Component.text("You cannot build here.", NamedTextColor.RED));
    }
}
