package com.yapcore.regions.listener;

import com.yapcore.regions.service.RegionServiceImpl;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

public final class RegionListener implements Listener {

    private final RegionServiceImpl regions;

    public RegionListener(JavaPlugin plugin, RegionServiceImpl regions) {
        this.regions = regions;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!regions.at(event.getBlock().getLocation()).isPresent()) {
            return;
        }
        if (!regions.canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cAdmin region — you cannot build here.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!regions.at(event.getBlock().getLocation()).isPresent()) {
            return;
        }
        if (!regions.canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cAdmin region — you cannot build here.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!regions.at(event.getBlock().getLocation()).isPresent()) {
            return;
        }
        if (!regions.canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cAdmin region — you cannot build here.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!regions.at(event.getBlock().getLocation()).isPresent()) {
            return;
        }
        if (!regions.canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cAdmin region — you cannot build here.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (!regions.at(victim.getLocation()).isPresent()) {
            return;
        }
        if (event.getDamager() instanceof Player attacker) {
            if (!regions.isPvpAllowed(attacker, victim)) {
                event.setCancelled(true);
                attacker.sendMessage("§cPvP disabled in this admin region.");
            }
            return;
        }
        if (!regions.isMobDamageAllowed(victim)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        var from = regions.at(event.getFrom());
        var to = regions.at(event.getTo());
        if (from.map(r -> r.id()).equals(to.map(r -> r.id()))) {
            return;
        }
        if (to.isPresent() && !regions.canEnter(event.getPlayer(), event.getTo())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cEntry denied in this admin region.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !regions.at(block.getLocation()).isPresent()) {
            return;
        }
        Player player = event.getPlayer();
        Material type = block.getType();
        String n = type.name();
        if (n.contains("CHEST") || n.contains("BARREL") || n.contains("SHULKER")
                || type == Material.FURNACE || type == Material.BLAST_FURNACE
                || type == Material.SMOKER || type == Material.HOPPER) {
            if (!regions.canOpenContainer(player, block.getLocation())) {
                event.setCancelled(true);
                player.sendMessage("§cAdmin region — no chest access.");
            }
            return;
        }
        if (n.contains("DOOR") || n.contains("GATE") || n.contains("BUTTON") || n.contains("LEVER")) {
            if (!regions.canInteract(player, block.getLocation())) {
                event.setCancelled(true);
                player.sendMessage("§cAdmin region — interaction denied.");
            }
        }
    }
}
