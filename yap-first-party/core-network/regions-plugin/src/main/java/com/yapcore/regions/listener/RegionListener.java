package com.yapcore.regions.listener;

import com.yapcore.regions.RegionBoundaryNotify;
import com.yapcore.regions.RegionsConfig;
import com.yapcore.regions.service.RegionServiceImpl;
import io.papermc.paper.event.entity.EntityMoveEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.WeatherType;
import org.bukkit.block.Block;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RegionListener implements Listener {

    private final RegionServiceImpl regions;
    private final RegionBoundaryNotify notify;
    /** Players we forced clear weather for — reset only when they leave such a region. */
    private final Set<UUID> clearWeatherForced = ConcurrentHashMap.newKeySet();

    public RegionListener(RegionsConfig config, RegionServiceImpl regions) {
        this.regions = regions;
        this.notify = new RegionBoundaryNotify(config, regions);
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
    public void onAnyDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (!regions.at(victim.getLocation()).isPresent()) {
            return;
        }
        if (!regions.isDamageAllowed(victim.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player attacker = resolvePlayerDamager(event.getDamager());
        if (attacker != null
                && regions.at(attacker.getLocation()).isPresent()
                && !regions.isDamageAllowed(attacker.getLocation())) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (!regions.at(victim.getLocation()).isPresent()) {
            return;
        }
        if (!regions.isDamageAllowed(victim.getLocation())) {
            event.setCancelled(true);
            return;
        }
        if (attacker != null) {
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

    private static Player resolvePlayerDamager(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        if (damager instanceof Tameable tameable && tameable.getOwner() instanceof Player owner) {
            return owner;
        }
        return null;
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
            return;
        }
        Player player = event.getPlayer();
        notify.onCross(player, from, to);
        applyRegionWeather(player, event.getTo());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) {
            return;
        }
        var from = regions.at(event.getFrom());
        var to = regions.at(event.getTo());
        if (from.map(r -> r.id()).equals(to.map(r -> r.id()))) {
            applyRegionWeather(event.getPlayer(), event.getTo());
            return;
        }
        if (to.isPresent() && !regions.canEnter(event.getPlayer(), event.getTo())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cEntry denied in this admin region.");
            return;
        }
        notify.onCross(event.getPlayer(), from, to);
        applyRegionWeather(event.getPlayer(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Optional<com.yapcore.regions.AdminRegion> here = regions.at(player.getLocation());
        here.ifPresent(r -> notify.enter(player, r));
        applyRegionWeather(player, player.getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        clearWeatherForced.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHostileMove(EntityMoveEvent event) {
        if (!event.hasChangedBlock()) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Enemy)) {
            return;
        }
        if (intentionalSpawn(entity)) {
            return;
        }
        Location to = event.getTo();
        if (to == null || regions.isMobEntryAllowed(to)) {
            return;
        }
        event.setCancelled(true);
        if (!regions.isMobEntryAllowed(event.getFrom())) {
            entity.remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHostileTeleport(EntityTeleportEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Enemy)) {
            return;
        }
        if (intentionalSpawn(entity)) {
            return;
        }
        Location to = event.getTo();
        if (to == null || regions.isMobEntryAllowed(to)) {
            return;
        }
        event.setCancelled(true);
        if (!regions.isMobEntryAllowed(event.getFrom())) {
            entity.remove();
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
        if (isIntentionalSpawnReason(event.getSpawnReason())) {
            return;
        }
        if (event.getEntity() instanceof Enemy && !regions.isMobEntryAllowed(event.getLocation())) {
            event.setCancelled(true);
            return;
        }
        if (!regions.isMobSpawningAllowed(event.getLocation())) {
            event.setCancelled(true);
        }
    }

    private static boolean isIntentionalSpawnReason(CreatureSpawnEvent.SpawnReason reason) {
        return reason == CreatureSpawnEvent.SpawnReason.CUSTOM
                || reason == CreatureSpawnEvent.SpawnReason.COMMAND
                || reason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG
                || reason == CreatureSpawnEvent.SpawnReason.SPAWNER;
    }

    private static boolean intentionalSpawn(Entity entity) {
        try {
            var reason = entity.getEntitySpawnReason();
            return reason != null && isIntentionalSpawnReason(reason);
        } catch (Throwable ignored) {
            return false;
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
        // Parkour-friendly: doors / plates / buttons use USE (default allow).
        if (n.contains("DOOR") || n.contains("GATE") || n.contains("BUTTON")
                || n.contains("LEVER") || n.contains("PRESSURE_PLATE") || n.contains("TRIPWIRE")) {
            if (!regions.canUse(player, block.getLocation())) {
                event.setCancelled(true);
                player.sendMessage("§cAdmin region — use denied.");
            }
            return;
        }
        if (n.contains("FLOWER_POT") || type == Material.LECTERN || type == Material.JUKEBOX
                || type == Material.NOTE_BLOCK || type == Material.BELL) {
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
        boolean tnt = entity instanceof TNTPrimed;
        boolean creeper = entity instanceof Creeper;
        if (!tnt && !creeper) {
            return;
        }
        // Flag applies to blocks in the region, not only the entity standing in it.
        // A creeper just outside spawn still craters the cuboid otherwise.
        Location origin = event.getLocation();
        boolean originDenied = origin != null && !explosionAllowed(tnt, origin);
        var blocks = event.blockList().iterator();
        while (blocks.hasNext()) {
            Block block = blocks.next();
            if (block == null || !explosionAllowed(tnt, block.getLocation())) {
                blocks.remove();
            }
        }
        if (originDenied) {
            event.setCancelled(true);
            event.blockList().clear();
        }
    }

    private boolean explosionAllowed(boolean tnt, Location location) {
        return tnt ? regions.isTntAllowed(location) : regions.isCreeperExplosionAllowed(location);
    }

    private void applyRegionWeather(Player player, Location location) {
        if (regions.forcesClearWeather(location)) {
            player.setPlayerWeather(WeatherType.CLEAR);
            clearWeatherForced.add(player.getUniqueId());
            return;
        }
        if (clearWeatherForced.remove(player.getUniqueId())) {
            player.resetPlayerWeather();
        }
    }
}
