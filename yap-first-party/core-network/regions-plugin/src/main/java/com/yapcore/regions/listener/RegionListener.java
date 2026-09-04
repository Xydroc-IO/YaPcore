package com.yapcore.regions.listener;

import com.yapcore.regions.service.RegionServiceImpl;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
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
    public void onFireSpread(BlockSpreadEvent event) {
        if (!regions.at(event.getBlock().getLocation()).isPresent()) {
            return;
        }
        if (event.getSource().getType() != Material.FIRE && event.getSource().getType() != Material.SOUL_FIRE) {
            return;
        }
        if (!regions.isFireSpreadAllowed(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobSpawn(CreatureSpawnEvent event) {
        if (!regions.at(event.getLocation()).isPresent()) {
            return;
        }
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM
                || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG
                || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) {
            return;
        }
        if (!regions.isMobSpawningAllowed(event.getLocation())) {
            event.setCancelled(true);
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!regions.at(event.getPlayer().getLocation()).isPresent()) {
            return;
        }
        if (!regions.canDropItems(event.getPlayer(), event.getPlayer().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cAdmin region — item drop denied.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!regions.at(player.getLocation()).isPresent()) {
            return;
        }
        if (!regions.canPickupItems(player, player.getLocation())) {
            event.setCancelled(true);
            player.sendMessage("§cAdmin region — item pickup denied.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof TNTPrimed) {
            if (!regions.isTntAllowed(event.getLocation())) {
                event.setCancelled(true);
                event.blockList().clear();
            }
            return;
        }
        if (entity instanceof Creeper) {
            if (!regions.isCreeperExplosionAllowed(event.getLocation())) {
                event.setCancelled(true);
                event.blockList().clear();
            }
        }
    }
}
