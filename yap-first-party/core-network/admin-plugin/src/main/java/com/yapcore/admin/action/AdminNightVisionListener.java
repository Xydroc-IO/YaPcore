package com.yapcore.admin.action;

import com.yapcore.admin.AdminPlugin;
import com.yapcore.sched.YapSched;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffectType;

/** Restores staff night vision on join / respawn / accidental clear. */
public final class AdminNightVisionListener implements Listener {

    private final AdminPlugin plugin;

    public AdminNightVisionListener(AdminPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Delay so LuckPerms / network OP sync can attach staff nodes first.
        YapSched.entityLater(plugin, player,
                () -> plugin.actions().nightVision().restoreIfNeeded(player), 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        YapSched.entityLater(plugin, player,
                () -> plugin.actions().nightVision().restoreIfNeeded(player), 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        plugin.actions().nightVision().onQuit(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getAction() != EntityPotionEffectEvent.Action.REMOVED
                && event.getAction() != EntityPotionEffectEvent.Action.CLEARED) {
            return;
        }
        PotionEffectType type = event.getOldEffect() != null
                ? event.getOldEffect().getType()
                : event.getModifiedType();
        if (type == null || !type.equals(PotionEffectType.NIGHT_VISION)) {
            return;
        }
        if (!plugin.actions().nightVision().isUnlimited(player.getUniqueId())) {
            return;
        }
        // Staff turned it off via removePotionEffect — skip reapply when no longer tracked.
        // CLEARED/REMOVED from milk/death still tracked → reapply.
        plugin.actions().nightVision().reapplyIfUnlimited(player);
    }
}
