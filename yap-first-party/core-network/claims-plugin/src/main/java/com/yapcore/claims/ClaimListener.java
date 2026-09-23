package com.yapcore.claims;

import org.bukkit.Material;
import org.bukkit.WeatherType;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class ClaimListener implements Listener {

    private final JavaPlugin plugin;
    private final ClaimService claims;
    private final Set<UUID> clearWeatherForced = ConcurrentHashMap.newKeySet();

    public ClaimListener(JavaPlugin plugin, ClaimService claims) {
        this.plugin = plugin;
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
        Player player = resolvePlayerDamager(event.getRemover());
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnyDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        var claim = claims.getAt(victim.getLocation());
        if (claim.isEmpty()) {
            return;
        }
        if (!claims.isDamageAllowed(victim.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        ClaimExplosionRules.Kind boom = ClaimExplosionRules.kindOf(event.getDamager());
        if (boom != ClaimExplosionRules.Kind.NONE
                && event.getEntity() instanceof ArmorStand stand
                && !explosionAllowed(boom, stand.getLocation())) {
            event.setCancelled(true);
            return;
        }
        Player attacker = resolvePlayerDamager(event.getDamager());
        if (attacker != null
                && claims.getAt(attacker.getLocation()).isPresent()
                && !claims.isDamageAllowed(attacker.getLocation())) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        var claim = claims.getAt(victim.getLocation());
        if (claim.isEmpty()) {
            return;
        }
        if (!claims.isDamageAllowed(victim.getLocation())) {
            event.setCancelled(true);
            return;
        }
        if (attacker == null) {
            return;
        }
        if (!attacker.hasPermission("yapdata.claims.admin")
                && !claims.isPvpAllowed(attacker, victim)) {
            event.setCancelled(true);
            attacker.sendMessage("§cPvP disabled in this claim.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (resolvePlayerDamager(event.getDamager()) != null) {
            return;
        }
        if (!claims.isMobDamageAllowed(victim)) {
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
    public void onMove(org.bukkit.event.player.PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        var fromClaim = claims.getAt(event.getFrom());
        var toClaim = claims.getAt(event.getTo());
        if (fromClaim.map(Claim::id).equals(toClaim.map(Claim::id))) {
            return;
        }
        if (!claims.canEnter(event.getPlayer(), event.getTo())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cEntry denied in this claim.");
            return;
        }
        Player player = event.getPlayer();
        fromClaim.flatMap(c -> claims.message(c.id(), ClaimMessageKind.FAREWELL))
                .ifPresent(player::sendMessage);
        toClaim.flatMap(c -> claims.message(c.id(), ClaimMessageKind.GREETING))
                .ifPresent(player::sendMessage);
        applyClaimWeather(player, event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        clearWeatherForced.remove(event.getPlayer().getUniqueId());
        claims.clearBorderView(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHostileMove(io.papermc.paper.event.entity.EntityMoveEvent event) {
        if (!event.hasChangedBlock()) {
            return;
        }
        org.bukkit.entity.LivingEntity entity = event.getEntity();
        if (!(entity instanceof org.bukkit.entity.Enemy)) {
            return;
        }
        if (intentionalSpawn(entity)) {
            return;
        }
        org.bukkit.Location to = event.getTo();
        if (to == null || claims.isMobEntryAllowed(to)) {
            return;
        }
        event.setCancelled(true);
        if (!claims.isMobEntryAllowed(event.getFrom())) {
            entity.remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHostileTeleport(org.bukkit.event.entity.EntityTeleportEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof org.bukkit.entity.Enemy)) {
            return;
        }
        if (intentionalSpawn(entity)) {
            return;
        }
        org.bukkit.Location to = event.getTo();
        if (to == null || claims.isMobEntryAllowed(to)) {
            return;
        }
        event.setCancelled(true);
        if (!claims.isMobEntryAllowed(event.getFrom())) {
            entity.remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!claims.canDropItems(event.getPlayer(), event.getPlayer().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cClaimed land — item drop denied.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!claims.canPickupItems(player, player.getLocation())) {
            event.setCancelled(true);
            player.sendMessage("§cClaimed land — item pickup denied.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNetherPortal(PlayerPortalEvent event) {
        if (!blockClaimNetherPortal(event.getPlayer(), event.getFrom(), event.getCause())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNetherEntityPortal(EntityPortalEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        PlayerTeleportEvent.TeleportCause cause;
        if (event.getPortalType() == org.bukkit.PortalType.NETHER) {
            cause = PlayerTeleportEvent.TeleportCause.NETHER_PORTAL;
        } else if (event.getPortalType() == org.bukkit.PortalType.ENDER) {
            cause = PlayerTeleportEvent.TeleportCause.END_PORTAL;
        } else if (event.getPortalType() == org.bukkit.PortalType.END_GATEWAY) {
            cause = PlayerTeleportEvent.TeleportCause.END_GATEWAY;
        } else {
            cause = PlayerTeleportEvent.TeleportCause.UNKNOWN;
        }
        if (!blockClaimNetherPortal(player, event.getFrom(), cause)) {
            return;
        }
        event.setCancelled(true);
    }

    /**
     * Folia sometimes fires {@link PlayerTeleportEvent} without {@link PlayerPortalEvent}.
     * Skip when already handled as a portal event subclass.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNetherTeleport(PlayerTeleportEvent event) {
        if (event instanceof PlayerPortalEvent) {
            return;
        }
        if (!blockClaimNetherPortal(event.getPlayer(), event.getFrom(), event.getCause())) {
            return;
        }
        event.setCancelled(true);
    }

    private boolean blockClaimNetherPortal(
            Player player, org.bukkit.Location from, PlayerTeleportEvent.TeleportCause cause) {
        if (cause != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                && cause != PlayerTeleportEvent.TeleportCause.END_PORTAL
                && cause != PlayerTeleportEvent.TeleportCause.END_GATEWAY) {
            return false;
        }
        org.bukkit.Location probe = from != null ? from : player.getLocation();
        if (claims.canUseNetherPortal(player, probe)) {
            return false;
        }
        String kind = cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL ? "nether" : "end";
        player.sendMessage("§cClaimed " + kind + " portal — only the owner and trusted players can use it.");
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplosionPrime(ExplosionPrimeEvent event) {
        ClaimExplosionRules.Kind kind = ClaimExplosionRules.kindOf(event.getEntity());
        if (kind == ClaimExplosionRules.Kind.NONE) {
            return;
        }
        // Stop the blast entirely when the fuse entity sits in protected claim land.
        if (!explosionAllowed(kind, event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        ClaimExplosionRules.Kind kind = ClaimExplosionRules.kindOf(event.getEntity());
        if (kind == ClaimExplosionRules.Kind.NONE) {
            return;
        }
        // Flag applies per-block: a creeper just outside still cannot crater claimed land.
        org.bukkit.Location origin = event.getLocation();
        boolean originDenied = origin != null && !explosionAllowed(kind, origin);
        boolean anyClaimBlockDenied = false;
        var blocks = event.blockList().iterator();
        while (blocks.hasNext()) {
            Block block = blocks.next();
            if (block == null || !explosionAllowed(kind, block.getLocation())) {
                blocks.remove();
                anyClaimBlockDenied = true;
            }
        }
        if (originDenied || (anyClaimBlockDenied && event.blockList().isEmpty())) {
            event.setCancelled(true);
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreakExplosion(HangingBreakEvent event) {
        if (event.getCause() != HangingBreakEvent.RemoveCause.EXPLOSION) {
            return;
        }
        Entity remover = event instanceof HangingBreakByEntityEvent byEntity
                ? byEntity.getRemover() : null;
        ClaimExplosionRules.Kind kind = ClaimExplosionRules.kindOf(remover);
        if (kind == ClaimExplosionRules.Kind.NONE) {
            // Unknown source — still honor default deny explosives in claims.
            if (!claims.isCreeperExplosionAllowed(event.getEntity().getLocation())
                    || !claims.isTntAllowed(event.getEntity().getLocation())) {
                event.setCancelled(true);
            }
            return;
        }
        if (!explosionAllowed(kind, event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    private boolean explosionAllowed(ClaimExplosionRules.Kind kind, org.bukkit.Location location) {
        return switch (kind) {
            case TNT -> claims.isTntAllowed(location);
            case CREEPER -> claims.isCreeperExplosionAllowed(location);
            case NONE -> true;
        };
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFireSpread(BlockSpreadEvent event) {
        if (event.getSource().getType() != Material.FIRE && event.getSource().getType() != Material.SOUL_FIRE) {
            return;
        }
        if (!claims.isFireSpreadAllowed(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobSpawn(CreatureSpawnEvent event) {
        if (isIntentionalSpawnReason(event.getSpawnReason())) {
            return;
        }
        if (event.getEntity() instanceof org.bukkit.entity.Enemy && !claims.isMobEntryAllowed(event.getLocation())) {
            event.setCancelled(true);
            return;
        }
        if (!claims.isMobSpawningAllowed(event.getLocation())) {
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

    /**
     * Non-players stepping on pressure plates — cancel so iron doors / redstone doors stay shut.
     * Players use {@link PlayerInteractEvent} ({@link Action#PHYSICAL}) and are unaffected.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPressurePlate(EntityInteractEvent event) {
        Block block = event.getBlock();
        if (block == null) {
            return;
        }
        if (!ClaimPressurePlateRules.allow(
                claims.config().claimsEnabled(),
                claims.config().claimsMobsActivatePressurePlates(),
                event.getEntity() instanceof Player,
                ClaimPressurePlateRules.isPressurePlate(block.getType().name()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        Material hand = player.getInventory().getItemInMainHand().getType();
        Material claimTool = claims.config().claimsTool();
        Material inspect = claims.config().claimsInspectTool();

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && hand == claimTool) {
            event.setCancelled(true);
            try {
                player.sendMessage(claims.handleShovel(player, block.getLocation()));
            } catch (Exception e) {
                player.sendMessage("§cClaim error: " + e.getMessage());
                plugin.getLogger().log(Level.WARNING, "claim shovel", e);
            }
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && hand == inspect) {
            event.setCancelled(true);
            var opt = claims.getAt(block.getLocation());
            if (opt.isEmpty()) {
                player.sendMessage("§7Wilderness — not claimed.");
            } else {
                Claim c = opt.get();
                player.sendMessage("§aClaim §f#" + c.id() + " §7· " + c.area() + " blocks · §f"
                        + c.minX() + "," + c.minZ() + " → " + c.maxX() + "," + c.maxZ());
                ClaimVisualizer.show(plugin, player, c, claims.config().claimsVisualSeconds());
            }
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Material type = block.getType();
            String n = type.name();
            if (n.contains("CHEST") || n.contains("BARREL") || n.contains("SHULKER")
                    || type == Material.FURNACE || type == Material.BLAST_FURNACE
                    || type == Material.SMOKER || type == Material.HOPPER) {
                if (!claims.canOpenContainer(player, block.getLocation())) {
                    event.setCancelled(true);
                    player.sendMessage("§cClaimed — no chest access.");
                }
                return;
            }
            if (n.contains("DOOR") || n.contains("GATE") || n.contains("BUTTON")
                    || n.contains("LEVER") || n.contains("PRESSURE_PLATE")) {
                // Parkour-friendly: USE defaults allow; INTERACT no longer gates doors.
                if (!claims.canUse(player, block.getLocation())) {
                    event.setCancelled(true);
                    player.sendMessage("§cClaimed — use denied.");
                }
            }
        }
    }

    /**
     * Client clear-weather overlay for claims. Skips when an admin region covers the block
     * (YaPRegions owns weather there).
     */
    private void applyClaimWeather(Player player, org.bukkit.Location location) {
        if (com.yapcore.regions.RegionServices.find().flatMap(s -> s.at(location)).isPresent()) {
            return;
        }
        if (claims.forcesClearWeather(location)) {
            player.setPlayerWeather(WeatherType.CLEAR);
            clearWeatherForced.add(player.getUniqueId());
            return;
        }
        if (clearWeatherForced.remove(player.getUniqueId())) {
            player.resetPlayerWeather();
        }
    }
}
