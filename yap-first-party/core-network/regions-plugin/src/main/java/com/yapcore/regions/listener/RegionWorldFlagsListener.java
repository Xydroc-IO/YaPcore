package com.yapcore.regions.listener;

import com.yapcore.regions.service.RegionServiceImpl;
import com.yapcore.sched.YapSched;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Hub / spawn world flags: hunger, farmland, frames, armor stands, leaf decay, pistons, vehicles.
 */
public final class RegionWorldFlagsListener implements Listener {

    private final JavaPlugin plugin;
    private final RegionServiceImpl regions;

    public RegionWorldFlagsListener(JavaPlugin plugin, RegionServiceImpl regions) {
        this.plugin = plugin;
        this.regions = regions;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getFoodLevel() >= player.getFoodLevel()) {
            return;
        }
        if (!regions.isHungerAllowed(player.getLocation())) {
            event.setCancelled(true);
            // Keep full — vitals sync / soft-switch can leave a drained bar; cancel alone
            // only stops further loss.
            fillFood(player);
            event.setFoodLevel(20);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Delay so PlayerData vitals apply first, then re-fill on safe hubs / spawn pads.
        YapSched.entityLater(plugin, player, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (!regions.isHungerAllowed(player.getLocation())) {
                fillFood(player);
            }
        }, 40L);
    }

    private static void fillFood(Player player) {
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setExhaustion(0f);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleItemUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }
        ItemStack hand = event.getItem();
        if (hand == null) {
            return;
        }
        String n = hand.getType().name();
        if (!n.contains("BOAT") && !n.contains("MINECART") && hand.getType() != Material.MINECART) {
            return;
        }
        Location loc = event.getClickedBlock() != null
                ? event.getClickedBlock().getLocation()
                : event.getPlayer().getLocation();
        if (!regions.at(loc).isPresent()) {
            return;
        }
        if (com.yapcore.sched.StaffBypass.land(event.getPlayer())) {
            return;
        }
        if (!regions.isVehiclePlaceAllowed(loc)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cAdmin region — vehicles denied.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFarmlandTrample(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.FARMLAND) {
            return;
        }
        if (!regions.at(block.getLocation()).isPresent()) {
            return;
        }
        if (!regions.isFarmlandTrampleAllowed(block.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityFarmland(EntityChangeBlockEvent event) {
        if (event.getBlock().getType() != Material.FARMLAND) {
            return;
        }
        Location loc = event.getBlock().getLocation();
        if (!regions.at(loc).isPresent()) {
            return;
        }
        if (!regions.isFarmlandTrampleAllowed(loc)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLeafDecay(LeavesDecayEvent event) {
        Location loc = event.getBlock().getLocation();
        if (!regions.at(loc).isPresent()) {
            return;
        }
        if (!regions.isLeafDecayAllowed(loc)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (blocksPiston(event.getBlock().getLocation()) || anyDenied(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (blocksPiston(event.getBlock().getLocation()) || anyDenied(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Entity target = event.getRightClicked();
        Location loc = target.getLocation();
        if (!regions.at(loc).isPresent()) {
            return;
        }
        Player player = event.getPlayer();
        if (target instanceof ItemFrame || target instanceof Painting) {
            if (!regions.isItemFrameAllowed(loc) && !com.yapcore.sched.StaffBypass.land(player)) {
                event.setCancelled(true);
                player.sendMessage("§cAdmin region — item frames protected.");
            }
            return;
        }
        if (target instanceof ArmorStand) {
            if (!regions.isArmorStandAllowed(loc) && !com.yapcore.sched.StaffBypass.land(player)) {
                event.setCancelled(true);
                player.sendMessage("§cAdmin region — armor stands protected.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        Location loc = victim.getLocation();
        if (!regions.at(loc).isPresent()) {
            return;
        }
        Player attacker = event.getDamager() instanceof Player p ? p : null;
        if (attacker != null && com.yapcore.sched.StaffBypass.land(attacker)) {
            return;
        }
        if ((victim instanceof ItemFrame || victim instanceof Painting) && !regions.isItemFrameAllowed(loc)) {
            event.setCancelled(true);
            return;
        }
        if (victim instanceof ArmorStand && !regions.isArmorStandAllowed(loc)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        Location loc = event.getBlock().getLocation();
        if (!regions.at(loc).isPresent()) {
            return;
        }
        Player player = event.getPlayer();
        if (player != null && com.yapcore.sched.StaffBypass.land(player)) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity instanceof ArmorStand && !regions.isArmorStandAllowed(loc)) {
            event.setCancelled(true);
            if (player != null) {
                player.sendMessage("§cAdmin region — armor stands denied.");
            }
            return;
        }
        if (entity instanceof ItemFrame && !regions.isItemFrameAllowed(loc)) {
            event.setCancelled(true);
            if (player != null) {
                player.sendMessage("§cAdmin region — item frames denied.");
            }
            return;
        }
        if (entity instanceof Vehicle && !regions.isVehiclePlaceAllowed(loc)) {
            event.setCancelled(true);
            if (player != null) {
                player.sendMessage("§cAdmin region — vehicles denied.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        Location loc = event.getVehicle().getLocation();
        if (!regions.at(loc).isPresent()) {
            return;
        }
        if (event.getAttacker() instanceof Player player && com.yapcore.sched.StaffBypass.land(player)) {
            return;
        }
        if (!regions.isVehicleDestroyAllowed(loc)) {
            event.setCancelled(true);
        }
    }

    private boolean blocksPiston(Location loc) {
        return regions.at(loc).isPresent() && !regions.isPistonsAllowed(loc);
    }

    private boolean anyDenied(java.util.List<Block> blocks) {
        for (Block block : blocks) {
            Location loc = block.getLocation();
            if (regions.at(loc).isPresent() && !regions.isPistonsAllowed(loc)) {
                return true;
            }
        }
        return false;
    }
}
