package com.yapcore.essentials.listener;

import com.yapcore.essentials.EssentialsConfig;
import com.yapcore.essentials.store.SpawnStore;
import com.yapcore.essentials.util.TeleportHelper;
import com.yapcore.sched.YapSched;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Optional hub-style join: always land at this server's /setspawn. */
public final class SpawnJoinListener implements Listener {

    private final JavaPlugin plugin;
    private final EssentialsConfig config;
    private final SpawnStore spawnStore;

    public SpawnJoinListener(JavaPlugin plugin, EssentialsConfig config, SpawnStore spawnStore) {
        this.plugin = plugin;
        this.config = config;
        this.spawnStore = spawnStore;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!config.spawnTeleportOnJoin()) {
            return;
        }
        Player player = event.getPlayer();
        // After PlayerData unfreeze / chunk attach
        YapSched.entityLater(plugin, player, () -> {
            if (!player.isOnline()) {
                return;
            }
            EssentialsConfig live = config;
            if (plugin instanceof com.yapcore.essentials.EssentialsPlugin ess) {
                live = ess.essentialsConfig();
            }
            if (live == null || !live.spawnTeleportOnJoin()) {
                return;
            }
            Location spawn = spawnStore.spawn();
            if (spawn == null || spawn.getWorld() == null) {
                return;
            }
            TeleportHelper.teleport(plugin, player, spawn);
        }, 20L);
    }
}
