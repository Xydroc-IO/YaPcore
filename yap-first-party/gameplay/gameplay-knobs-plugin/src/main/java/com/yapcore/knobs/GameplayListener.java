package com.yapcore.knobs;

import com.yapcore.sched.YapSched;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.concurrent.ThreadLocalRandom;

/** Wires {@code gameplay.*} and settings that are Paper-event expressible. */
public final class GameplayListener implements Listener {

    private final GameplayKnobsPlugin plugin;
    private final KnobsConfig config;

    public GameplayListener(GameplayKnobsPlugin plugin, KnobsConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlindness(EntityPotionEffectEvent event) {
        if (!config.enabled()) {
            return;
        }
        double mult = config.gameplay().entityBlindnessMultiplier();
        if (mult == 1.0 || mult <= 0) {
            return;
        }
        if (event.getAction() != EntityPotionEffectEvent.Action.ADDED
                && event.getAction() != EntityPotionEffectEvent.Action.CHANGED) {
            return;
        }
        PotionEffect neu = event.getNewEffect();
        if (neu == null || neu.getType() != PotionEffectType.BLINDNESS) {
            return;
        }
        int dur = Math.max(1, (int) Math.round(neu.getDuration() * mult));
        event.setCancelled(true);
        event.getEntity().addPotionEffect(new PotionEffect(
                PotionEffectType.BLINDNESS, dur, neu.getAmplifier(),
                neu.isAmbient(), neu.hasParticles(), neu.hasIcon()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVoidDamage(EntityDamageEvent event) {
        if (!config.enabled()) {
            return;
        }
        if (event.getCause() != EntityDamageEvent.DamageCause.VOID) {
            return;
        }
        if (config.gameplay().useVoidDamageFix() && event.getEntity() instanceof Player player) {
            // Soft-cap void damage spikes (Paper void can one-shot); keep lethal but fair
            if (event.getDamage() > player.getHealth() + 4) {
                event.setDamage(player.getHealth() + 2);
            }
        }
        if (event.getEntity() instanceof Player player && config.gameplay().netheriteFireResistance()) {
            // netherite fire handled in onFire below
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFire(EntityDamageEvent event) {
        if (!config.enabled() || !config.gameplay().netheriteFireResistance()) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        EntityDamageEvent.DamageCause c = event.getCause();
        if (c != EntityDamageEvent.DamageCause.FIRE
                && c != EntityDamageEvent.DamageCause.FIRE_TICK
                && c != EntityDamageEvent.DamageCause.LAVA
                && c != EntityDamageEvent.DamageCause.HOT_FLOOR) {
            return;
        }
        if (wearingFullNetherite(player)) {
            event.setCancelled(true);
            player.setFireTicks(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onResurrect(EntityResurrectEvent event) {
        if (!config.enabled() || !config.gameplay().totemWorksInVoid()) {
            return;
        }
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        // Allow totem even when last damage was void (Paper may cancel); force allow
        if (event.isCancelled()) {
            event.setCancelled(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCropGrow(BlockGrowEvent event) {
        if (!config.enabled()) {
            return;
        }
        double mod = config.gameplay().cropGrowthModifier();
        if (mod == 1.0) {
            return;
        }
        // Paper path: probabilistic cancel/accelerate. NMS path (E2) when crop-growth-nms=true.
        if (config.gameplay().cropGrowthNms()) {
            return; // Folia hook owns it
        }
        if (mod < 1.0) {
            if (ThreadLocalRandom.current().nextDouble() > mod) {
                event.setCancelled(true);
            }
        } else if (mod > 1.0) {
            // Chance to apply an extra growth step via natural re-fire is limited; skip cancel less often
            // Represent >1 as "never cancel" (already) — extra ticks need NMS (E2).
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGiveCommand(PlayerCommandPreprocessEvent event) {
        if (!config.enabled() || !config.disableGiveDropping()) {
            return;
        }
        String msg = event.getMessage().toLowerCase(java.util.Locale.ROOT);
        if (msg.startsWith("/give ") || msg.startsWith("/minecraft:give ")) {
            Player p = event.getPlayer();
            p.setMetadata("yapknobs.suppress-drop", new FixedMetadataValue(plugin, true));
            YapSched.globalLater(plugin, () -> p.removeMetadata("yapknobs.suppress-drop", plugin), 40L);
        }
    }

    private static boolean wearingFullNetherite(Player player) {
        ItemStack helm = player.getInventory().getHelmet();
        ItemStack chest = player.getInventory().getChestplate();
        ItemStack legs = player.getInventory().getLeggings();
        ItemStack boots = player.getInventory().getBoots();
        return isNetherite(helm) && isNetherite(chest) && isNetherite(legs) && isNetherite(boots);
    }

    private static boolean isNetherite(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        Material m = stack.getType();
        return m == Material.NETHERITE_HELMET
                || m == Material.NETHERITE_CHESTPLATE
                || m == Material.NETHERITE_LEGGINGS
                || m == Material.NETHERITE_BOOTS;
    }
}
