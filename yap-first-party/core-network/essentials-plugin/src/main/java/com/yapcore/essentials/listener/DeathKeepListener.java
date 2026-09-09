package com.yapcore.essentials.listener;

import com.yapcore.essentials.EssentialsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Keep inventory on death when {@code death.keep-inventory} is on (everyone),
 * or when the player has {@code yapessentials.keepinventory} (e.g. VIP).
 * Does not force drops when both are off — vanilla / gamerule / dungeons still apply.
 */
public final class DeathKeepListener implements Listener {

    public static final String PERM_KEEP_INVENTORY = "yapessentials.keepinventory";
    public static final String PERM_KEEP_XP = "yapessentials.keepinventory.xp";

    private final EssentialsPlugin plugin;

    public DeathKeepListener(EssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        var player = event.getEntity();
        boolean keepInv = plugin.essentialsConfig().keepInventory()
                || player.hasPermission(PERM_KEEP_INVENTORY);
        if (!keepInv) {
            return;
        }
        event.setKeepInventory(true);
        event.getDrops().clear();
        boolean keepXp = plugin.essentialsConfig().keepXp()
                || player.hasPermission(PERM_KEEP_XP);
        if (keepXp) {
            event.setKeepLevel(true);
            event.setDroppedExp(0);
        }
    }
}
