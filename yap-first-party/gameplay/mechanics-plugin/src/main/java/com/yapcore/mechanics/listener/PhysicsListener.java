package com.yapcore.mechanics.listener;

import com.yapcore.mechanics.service.MechanicsServiceImpl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerFishEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PhysicsListener implements Listener {

    private final MechanicsServiceImpl mechanics;
    private final Map<UUID, Long> fishStart = new ConcurrentHashMap<>();

    public PhysicsListener(MechanicsServiceImpl mechanics) {
        this.mechanics = mechanics;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        double mult = mechanics.fallDamageMultiplier(player);
        if (mult != 1.0) {
            event.setDamage(event.getDamage() * mult);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFishWait(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.FISHING) {
            fishStart.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        Player player = event.getPlayer();
        if (mechanics.config().staminaEnabled()) {
            if (!mechanics.consumeStamina(player, mechanics.config().fishCost())) {
                event.setCancelled(true);
                player.sendMessage("§cToo exhausted to reel in the catch.");
                return;
            }
        }
        double mult = mechanics.fishingXpMultiplier(player);
        Long start = fishStart.remove(player.getUniqueId());
        if (start != null && mult > 1.0) {
            long elapsedMs = System.currentTimeMillis() - start;
            long idealMs = (mechanics.config().minFishWait() + mechanics.config().maxFishWait()) / 2 * 50L;
            if (Math.abs(elapsedMs - idealMs) < idealMs / 3) {
                player.sendActionBar(net.kyori.adventure.text.Component.text(
                        "§aPerfect catch! §7(x" + String.format("%.2f", mult) + " bonus area)"));
            }
        }
    }
}
