package com.yapcore.vehicles.compat;

import com.yapcore.vehicles.engine.VehicleKeys;
import com.yapcore.vehicles.engine.VehicleServiceImpl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

/**
 * Intercepts mounts / interactions on foreign minecarts/boats and remaps to YaP.
 * Freezes foreign visuals so vanilla minecart physics does not fight YaP.
 */
public final class VehicleCompatListener implements Listener {

    private final VehicleCompatBridge bridge;
    private final VehicleKeys keys;
    private final VehicleServiceImpl api;

    public VehicleCompatListener(VehicleCompatBridge bridge, VehicleKeys keys, VehicleServiceImpl api) {
        this.bridge = bridge;
        this.keys = keys;
        this.api = api;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Entity mount = event.getMount();
        if (isForeignVisual(mount)) {
            event.setCancelled(true);
            api.getByEntity(mount).ifPresent(v -> v.enter(player, -1));
            return;
        }
        if (!bridge.shouldClaim(mount)) {
            return;
        }
        event.setCancelled(true);
        bridge.adapt(mount, player, null).ifPresent(v ->
                player.sendMessage("Remapped to YaP chassis (" + v.getType().id() + ")"));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Entity target = event.getRightClicked();
        if (isForeignVisual(target)) {
            event.setCancelled(true);
            api.getByEntity(target).ifPresent(v -> {
                if (event.getPlayer().hasPermission("yapvehicles.drive")) {
                    v.enter(event.getPlayer(), -1);
                }
            });
            return;
        }
        if (!bridge.shouldClaim(target)) {
            return;
        }
        if (event.getPlayer().isSneaking()) {
            return;
        }
        event.setCancelled(true);
        bridge.adapt(target, event.getPlayer(), null);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onForeignMove(VehicleMoveEvent event) {
        if (isForeignVisual(event.getVehicle())) {
            event.getVehicle().setVelocity(new Vector());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onForeignCollision(VehicleEntityCollisionEvent event) {
        if (isForeignVisual(event.getVehicle()) || isForeignVisual(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onForeignVehicleDamage(VehicleDamageEvent event) {
        if (isForeignVisual(event.getVehicle())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onForeignDamage(EntityDamageEvent event) {
        if (!isForeignVisual(event.getEntity())) {
            return;
        }
        event.setCancelled(true);
        if (event instanceof EntityDamageByEntityEvent by && by.getDamager() instanceof Player) {
            api.getByEntity(event.getEntity()).ifPresent(v ->
                    v.damage(event.getFinalDamage(), "attack"));
        }
    }

    private boolean isForeignVisual(Entity entity) {
        if (entity == null) {
            return false;
        }
        String role = entity.getPersistentDataContainer().get(keys.role, VehicleKeys.STRING);
        return "foreign_visual".equals(role);
    }
}
