package com.yapcore.claims;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;

final class ClaimListenerBuild implements Listener {

    private final ClaimService claims;

    ClaimListenerBuild(ClaimService claims) {
        this.claims = claims;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!claims.canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cClaimed land — you cannot build here"
                    + (claims.getAt(event.getBlock().getLocation()).map(c -> c.taxFrozen() ? " (tax frozen)" : "").orElse(""))
                    + ".");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!claims.canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cClaimed land — you cannot build here"
                    + (claims.getAt(event.getBlock().getLocation()).map(c -> c.taxFrozen() ? " (tax frozen)" : "").orElse(""))
                    + ".");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!claims.canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cClaimed land — you cannot build here.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!claims.canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cClaimed land — you cannot build here.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        if (!claims.canBuild(player, event.getBlock().getLocation())) {
            event.setCancelled(true);
            player.sendMessage("§cClaimed land — you cannot build here.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        if (!claims.canBuild(player, event.getBlock().getLocation())) {
            event.setCancelled(true);
            player.sendMessage("§cClaimed land — you cannot build here.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerChangeBlock(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!claims.canBuild(player, event.getBlock().getLocation())) {
            event.setCancelled(true);
            player.sendMessage("§cClaimed land — you cannot build here.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        if (!claims.canBuild(player, event.getEntity().getLocation())) {
            event.setCancelled(true);
            player.sendMessage("§cClaimed land — you cannot build here.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreakByPlayer(HangingBreakByEntityEvent event) {
        Player player = ClaimListenerEntities.resolvePlayerDamager(event.getRemover());
        if (player == null) {
            return;
        }
        if (!claims.canBuild(player, event.getEntity().getLocation())) {
            event.setCancelled(true);
            player.sendMessage("§cClaimed land — you cannot build here.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        if (!claims.canBuild(player, event.getBlock().getLocation())) {
            event.setCancelled(true);
            player.sendMessage("§cClaimed land — you cannot build here.");
        }
    }
}
