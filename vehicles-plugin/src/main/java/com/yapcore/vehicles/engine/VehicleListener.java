package com.yapcore.vehicles.engine;

import com.yapcore.vehicles.upgrades.UpgradeService;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class VehicleListener implements Listener {

    private final VehicleServiceImpl api;
    private final UpgradeService upgrades;

    public VehicleListener(VehicleServiceImpl api, UpgradeService upgrades) {
        this.api = api;
        this.upgrades = upgrades;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        // Armor stands also fire InteractAtEntity — handle there to avoid double consume
        if (api.getByEntity(event.getRightClicked()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Entity entity = event.getRightClicked();
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();

        var vehicleOpt = api.getByEntity(entity);
        if (vehicleOpt.isEmpty()) {
            return;
        }
        var vehicle = vehicleOpt.get();
        event.setCancelled(true);

        if (tryRefuel(player, hand, vehicle)) {
            return;
        }
        if (player.isSneaking() && upgrades != null && api.plugin().config().upgradesEnabled()) {
            var upId = upgrades.itemUpgradeId(hand);
            if (upId.isPresent()) {
                upgrades.get(upId.get()).ifPresent(up -> {
                    upgrades.install(vehicle, up, player);
                    if (hand.getAmount() > 1) {
                        hand.setAmount(hand.getAmount() - 1);
                    } else {
                        player.getInventory().setItemInMainHand(null);
                    }
                });
                return;
            }
        }

        if (!player.hasPermission("yapvehicles.drive")) {
            player.sendMessage("No permission to drive vehicles.");
            return;
        }
        vehicle.enter(player, -1);
    }

    private boolean tryRefuel(Player player, ItemStack hand, com.yapcore.vehicles.api.Vehicle vehicle) {
        var cfg = api.plugin().config();
        if (!cfg.fuelEnabled() || !vehicle.getType().usesFuel()) {
            return false;
        }
        if (cfg.fuelRequireSneak() && !player.isSneaking()) {
            return false;
        }
        Material fuelMat;
        try {
            fuelMat = Material.valueOf(cfg.fuelItem().toUpperCase());
        } catch (IllegalArgumentException ex) {
            fuelMat = Material.COAL;
        }
        if (hand == null || hand.getType() != fuelMat) {
            return false;
        }
        if (vehicle.getFuel() >= vehicle.getMaxFuel() - 0.01) {
            player.sendMessage("Tank is full.");
            return true;
        }
        double added = vehicle.refuel(cfg.fuelPerItem());
        if (added <= 0) {
            return true;
        }
        if (hand.getAmount() > 1) {
            hand.setAmount(hand.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
        player.sendMessage(String.format("Refueled +%.0f (%.0f / %.0f)",
                added, vehicle.getFuel(), vehicle.getMaxFuel()));
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        api.getByEntity(event.getDismounted()).ifPresent(vehicle -> {
            if (vehicle instanceof VehicleInstance instance) {
                instance.clearOccupant(player);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        api.getByPassenger(event.getPlayer()).ifPresent(v -> v.exit(event.getPlayer()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        api.getByEntity(event.getEntity()).ifPresent(vehicle -> {
            event.setCancelled(true);
            if (event instanceof EntityDamageByEntityEvent by
                    && by.getDamager() instanceof Player
                    && vehicle instanceof VehicleInstance) {
                vehicle.damage(event.getFinalDamage(), "attack");
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawnItem(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
            return;
        }
        ItemStack item = event.getItem();
        api.spawnItemType(item).ifPresent(typeId -> {
            event.setCancelled(true);
            Player player = event.getPlayer();
            if (!player.hasPermission("yapvehicles.spawn")) {
                player.sendMessage("No permission to spawn vehicles.");
                return;
            }
            var loc = player.getLocation();
            if (event.getClickedBlock() != null) {
                loc = event.getClickedBlock().getLocation().add(0.5, 1.0, 0.5);
                loc.setYaw(player.getLocation().getYaw());
            }
            try {
                api.spawn(loc, typeId, player);
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    player.getInventory().setItemInMainHand(null);
                }
                player.sendMessage("Spawned " + typeId);
            } catch (IllegalArgumentException | IllegalStateException ex) {
                player.sendMessage("Spawn failed: " + ex.getMessage());
            }
        });
    }
}
