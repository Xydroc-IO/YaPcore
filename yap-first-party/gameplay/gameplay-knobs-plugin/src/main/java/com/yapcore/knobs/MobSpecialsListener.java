package com.yapcore.knobs;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Endermite;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.concurrent.ThreadLocalRandom;

/** Per-mob Paper-API specials (creeper / phantom / bee / enderman / wolf / zombie). */
public final class MobSpecialsListener implements Listener {

    private final KnobsConfig config;

    public MobSpecialsListener(KnobsConfig config) {
        this.config = config;
    }

    static void applyOnSpawn(LivingEntity entity, KnobsConfig.MobKnobs knobs) {
        if (knobs == null) {
            return;
        }
        KnobsConfig.MobSpecials s = knobs.specials();
        if (entity instanceof Creeper creeper) {
            if (s.creeperExplosionRadius() != null) {
                creeper.setExplosionRadius(Math.max(0, (int) Math.round(s.creeperExplosionRadius())));
            }
            if (s.creeperMaxFuseTicks() != null) {
                creeper.setMaxFuseTicks(Math.max(1, s.creeperMaxFuseTicks()));
            }
            if (s.creeperCharged() != null) {
                creeper.setPowered(s.creeperCharged());
            }
        }
        if (entity instanceof Zombie zombie && s.zombieReinforcementChancePct() != null) {
            AttributeInstance inst = zombie.getAttribute(Attribute.SPAWN_REINFORCEMENTS);
            if (inst != null) {
                inst.setBaseValue(Math.max(0, Math.min(1.0, s.zombieReinforcementChancePct() / 100.0)));
            }
        }
        if (entity instanceof Wolf wolf && s.wolfSpawnRabidChance() != null
                && s.wolfSpawnRabidChance() > 0
                && ThreadLocalRandom.current().nextDouble() < s.wolfSpawnRabidChance()) {
            wolf.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20 * 60 * 10, 0));
            wolf.setAngry(true);
            wolf.setMetadata("yapknobs.rabid", new org.bukkit.metadata.FixedMetadataValue(
                    org.bukkit.Bukkit.getPluginManager().getPlugin("YaPGameplayKnobs"), true));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombust(EntityCombustEvent event) {
        if (!config.enabled()) {
            return;
        }
        if (!(event.getEntity() instanceof Phantom)) {
            return;
        }
        KnobsConfig.MobKnobs knobs = config.mob("phantom");
        if (knobs != null && knobs.specials().phantomBurnInDaylight() != null
                && !knobs.specials().phantomBurnInDaylight()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!config.enabled()) {
            return;
        }
        if (event.getEntity() instanceof Phantom && event.getTarget() instanceof Player player) {
            KnobsConfig.MobKnobs knobs = config.mob("phantom");
            if (knobs != null && Boolean.TRUE.equals(knobs.specials().phantomIgnorePlayersWithTorch())
                    && holdingTorch(player)) {
                event.setCancelled(true);
                event.setTarget(null);
            }
        }
        if (event.getEntity() instanceof Enderman && event.getTarget() instanceof Endermite) {
            KnobsConfig.MobKnobs knobs = config.mob("enderman");
            if (knobs != null && Boolean.FALSE.equals(knobs.specials().endermanAggressiveTowardsEndermites())) {
                event.setCancelled(true);
                event.setTarget(null);
            }
        }
        if (event.getEntity() instanceof Bee bee) {
            KnobsConfig.MobKnobs knobs = config.mob("bee");
            if (knobs == null) {
                return;
            }
            long time = bee.getWorld().getTime();
            boolean night = time > 13000 && time < 23000;
            boolean rain = bee.getWorld().hasStorm();
            if (night && Boolean.FALSE.equals(knobs.specials().beeCanWorkAtNight())) {
                event.setCancelled(true);
                bee.setCannotEnterHiveTicks(100);
            }
            if (rain && Boolean.FALSE.equals(knobs.specials().beeCanWorkInRain())) {
                event.setCancelled(true);
                bee.setCannotEnterHiveTicks(100);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMilkWolf(PlayerInteractEntityEvent event) {
        if (!config.enabled() || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!(event.getRightClicked() instanceof Wolf wolf)) {
            return;
        }
        KnobsConfig.MobKnobs knobs = config.mob("wolf");
        if (knobs == null || !Boolean.TRUE.equals(knobs.specials().wolfMilkCuresRabid())) {
            return;
        }
        ItemStack hand = event.getPlayer().getInventory().getItemInMainHand();
        if (hand.getType() != Material.MILK_BUCKET) {
            return;
        }
        if (!wolf.hasMetadata("yapknobs.rabid") && !wolf.isAngry()) {
            return;
        }
        wolf.removePotionEffect(PotionEffectType.STRENGTH);
        wolf.setAngry(false);
        wolf.removeMetadata("yapknobs.rabid",
                org.bukkit.Bukkit.getPluginManager().getPlugin("YaPGameplayKnobs"));
        hand.setType(Material.BUCKET);
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCreeperSpecial(PlayerInteractEntityEvent event) {
        if (!config.enabled() || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!(event.getRightClicked() instanceof Creeper creeper)) {
            return;
        }
        if (!(event.getPlayer().getVehicle() == creeper)) {
            return;
        }
        String perm = "yapknobs.special.creeper";
        if (!event.getPlayer().hasPermission(perm) && !event.getPlayer().hasPermission("yapknobs.special.*")) {
            return;
        }
        // Spacebar special while riding: toggled via jump input in RidableController — here allow shift-click charge toggle
        if (event.getPlayer().isSneaking()) {
            creeper.setPowered(!creeper.isPowered());
            event.setCancelled(true);
        }
    }

    private static boolean holdingTorch(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        return isTorch(main) || isTorch(off);
    }

    private static boolean isTorch(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        Material m = stack.getType();
        return m == Material.TORCH || m == Material.SOUL_TORCH || m == Material.REDSTONE_TORCH;
    }
}
