package com.yapcore.knobs;

import com.yapcore.sched.YapSched;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Applies encyclopedia knobs enforceable through Paper's API surface. */
public final class KnobsListener implements Listener {

    private final GameplayKnobsPlugin plugin;
    private final KnobsConfig config;
    private final Map<UUID, Long> lastBreedTick = new ConcurrentHashMap<>();

    public KnobsListener(GameplayKnobsPlugin plugin, KnobsConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!config.enabled()) {
            return;
        }
        LivingEntity entity = event.getEntity();
        KnobsConfig.MobKnobs knobs = config.mob(entity.getType().name());
        if (knobs == null) {
            return;
        }
        if (!knobs.enabled()) {
            event.setCancelled(true);
            return;
        }
        applyMob(entity, knobs);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if (!config.enabled()) {
            return;
        }
        for (Entity e : event.getEntities()) {
            if (!(e instanceof LivingEntity living) || living instanceof Player) {
                continue;
            }
            KnobsConfig.MobKnobs knobs = config.mob(living.getType().name());
            if (knobs == null || !knobs.enabled()) {
                continue;
            }
            YapSched.entity(plugin, living, () -> applyMob(living, knobs));
        }
    }

    static void applyMob(LivingEntity entity, KnobsConfig.MobKnobs knobs) {
        AttributeApplier.apply(entity, knobs);
        applyPickup(entity, knobs);
        MobSpecialsListener.applyOnSpawn(entity, knobs);
        if (entity instanceof Mob mob) {
            AiController.apply(mob, knobs, null);
            if (!knobs.retaliate()) {
                org.bukkit.Bukkit.getMobGoals().removeAllGoals(mob,
                        com.destroystokyo.paper.entity.ai.GoalType.TARGET);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (!config.enabled()) {
            return;
        }
        LivingEntity mother = event.getMother();
        KnobsConfig.MobKnobs knobs = config.mob(mother.getType().name());
        if (knobs == null) {
            return;
        }
        long now = mother.getWorld().getFullTime();
        Long last = lastBreedTick.get(mother.getUniqueId());
        if (last != null && now - last < knobs.breedingDelayTicks()) {
            event.setCancelled(true);
            return;
        }
        lastBreedTick.put(mother.getUniqueId(), now);
        LivingEntity father = event.getFather();
        if (father != null) {
            lastBreedTick.put(father.getUniqueId(), now);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        if (!config.enabled()) {
            return;
        }
        KnobsConfig.MobKnobs knobs = config.mob(event.getEntityType().name());
        if (knobs != null && knobs.alwaysDropExp() && event.getDroppedExp() <= 0) {
            event.setDroppedExp(Math.max(1, (int) (event.getEntity().getMaxHealth() / 4)));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!config.enabled()) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity living) || living instanceof Player) {
            return;
        }
        KnobsConfig.MobKnobs knobs = config.mob(living.getType().name());
        if (knobs == null) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.DROWNING
                && !knobs.takesDamageFromWater()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (!config.enabled()) {
            return;
        }
        Entity ent = event.getEntity();
        if (config.projectilesBypassMobGriefing() && ent instanceof Projectile) {
            event.blockList().clear();
            return;
        }
        if (ent != null) {
            KnobsConfig.MobKnobs knobs = config.mob(ent.getType().name());
            if (knobs != null && knobs.bypassMobGriefing()) {
                event.blockList().clear();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChangeBlock(EntityChangeBlockEvent event) {
        if (!config.enabled()) {
            return;
        }
        Entity ent = event.getEntity();
        KnobsConfig.MobKnobs knobs = config.mob(ent.getType().name());
        if (knobs != null && knobs.bypassMobGriefing()) {
            event.setCancelled(true);
            return;
        }
        if (knobs != null && knobs.specials().phantomAllowGriefing() != null
                && !knobs.specials().phantomAllowGriefing()
                && "PHANTOM".equals(ent.getType().name())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGiveDrop(PlayerDropItemEvent event) {
        if (!config.enabled() || !config.disableGiveDropping()) {
            return;
        }
        // Only suppress drops that look like /give spam: creative + recently given items
        // are hard to detect; cancel drops while player has metadata from console give.
        if (event.getPlayer().hasMetadata("yapknobs.suppress-drop")) {
            event.setCancelled(true);
            event.getItemDrop().remove();
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!config.enabled() || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!(event.getRightClicked() instanceof LivingEntity living) || living instanceof Player) {
            return;
        }
        KnobsConfig.MobKnobs knobs = config.mob(living.getType().name());
        if (knobs == null || !knobs.ridable()) {
            return;
        }
        Player player = event.getPlayer();
        String perm = "yapknobs.ride." + living.getType().name().toLowerCase(Locale.ROOT);
        if (!player.hasPermission(perm) && !player.hasPermission("yapknobs.ride.*")) {
            return;
        }
        if (living.getPassengers().contains(player)) {
            return;
        }
        if (!knobs.ridableInWater() && living.isInWater()) {
            return;
        }
        living.addPassenger(player);
        if (living instanceof Mob mob && knobs.controllable()) {
            AiController.clearMoveWhileRidden(mob);
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (!config.enabled()) {
            return;
        }
        if (!(event.getMount() instanceof LivingEntity living) || !(event.getEntity() instanceof Player)) {
            return;
        }
        KnobsConfig.MobKnobs knobs = config.mob(living.getType().name());
        if (knobs != null && knobs.ridable() && knobs.controllable() && living instanceof Mob mob) {
            AiController.clearMoveWhileRidden(mob);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        if (!config.enabled()) {
            return;
        }
        Entity vehicle = event.getDismounted();
        if (!(vehicle instanceof Mob mob)) {
            return;
        }
        KnobsConfig.MobKnobs knobs = config.mob(mob.getType().name());
        if (knobs != null) {
            YapSched.entity(plugin, mob, () -> AiController.apply(mob, knobs, plugin.getLogger()));
        }
    }

    private static void applyPickup(LivingEntity entity, KnobsConfig.MobKnobs knobs) {
        String mode = knobs.canPickUpLoot();
        if (mode == null || "default".equalsIgnoreCase(mode)) {
            return;
        }
        entity.setCanPickupItems(Boolean.parseBoolean(mode));
    }
}
