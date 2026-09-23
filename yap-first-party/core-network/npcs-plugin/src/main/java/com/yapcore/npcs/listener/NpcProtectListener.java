package com.yapcore.npcs.listener;

import com.yapcore.npcs.service.NpcServiceImpl;
import com.yapcore.regions.AdminRegion;
import com.yapcore.regions.FlagValue;
import com.yapcore.regions.RegionFlag;
import com.yapcore.regions.RegionService;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Protects tagged YaP NPCs from damage / death.
 * <p>
 * Region flag {@code npc-damage} ({@link RegionFlag#NPC_DAMAGE}): {@code deny} protects,
 * {@code allow} opts out. When unset, inherits {@code damage deny}. Outside admin regions
 * (or without YaPRegions), tagged NPCs stay protected.
 */
public final class NpcProtectListener implements Listener {

    private final JavaPlugin plugin;
    private final NpcServiceImpl npcs;

    public NpcProtectListener(JavaPlugin plugin, NpcServiceImpl npcs) {
        this.plugin = plugin;
        this.npcs = npcs;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDamage(EntityDamageEvent event) {
        if (!shouldProtect(event.getEntity())) {
            return;
        }
        event.setCancelled(true);
        event.setDamage(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCombust(EntityCombustEvent event) {
        if (shouldProtect(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onTransform(EntityTransformEvent event) {
        if (shouldProtect(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDeath(EntityDeathEvent event) {
        var idOpt = npcs.npcIdFromEntity(event.getEntity());
        if (idOpt.isEmpty()) {
            return;
        }
        if (!shouldProtect(event.getEntity())) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        String npcId = idOpt.get();
        YapSched.globalLater(plugin, () -> npcs.respawn(npcId), 5L);
    }

    private boolean shouldProtect(Entity entity) {
        if (npcs.npcIdFromEntity(entity).isEmpty()) {
            return false;
        }
        return !npcDamageAllowed(entity.getLocation());
    }

    /**
     * @return true if hits on tagged NPCs are allowed at this location
     */
    private static boolean npcDamageAllowed(Location location) {
        RegisteredServiceProvider<RegionService> reg =
                Bukkit.getServicesManager().getRegistration(RegionService.class);
        if (reg == null) {
            return false;
        }
        RegionService regions = reg.getProvider();
        var regionOpt = regions.at(location);
        if (regionOpt.isEmpty()) {
            return false;
        }
        AdminRegion region = regionOpt.get();
        FlagValue explicit = region.flags().get(RegionFlag.NPC_DAMAGE);
        if (explicit != null) {
            return explicit == FlagValue.ALLOW;
        }
        return regions.flagAt(location, RegionFlag.DAMAGE) == FlagValue.ALLOW;
    }
}
