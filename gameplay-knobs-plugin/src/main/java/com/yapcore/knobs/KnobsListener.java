package com.yapcore.knobs;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
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
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

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
        applyAttributes(entity, knobs);
        applyPickup(entity, knobs);
        if (entity instanceof Mob mob) {
            AiController.apply(mob, knobs, plugin.getLogger());
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
        if (config.projectilesBypassMobGriefing() && event.getEntity() instanceof Projectile) {
            // keep blocks
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChangeBlock(EntityChangeBlockEvent event) {
        // reserved for future block-entity grief knobs
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
            // Restore configured AI after ride
            plugin.getServer().getScheduler().runTask(plugin, () -> AiController.apply(mob, knobs, plugin.getLogger()));
        }
    }

    private static void applyAttributes(LivingEntity entity, KnobsConfig.MobKnobs knobs) {
        AttributeInstance maxHealth = entity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null && knobs.maxHealth() > 0) {
            maxHealth.setBaseValue(knobs.maxHealth());
            entity.setHealth(Math.min(entity.getHealth(), knobs.maxHealth()));
        }
        AttributeInstance scale = entity.getAttribute(Attribute.SCALE);
        if (scale != null && knobs.scale() > 0) {
            scale.setBaseValue(knobs.scale());
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
