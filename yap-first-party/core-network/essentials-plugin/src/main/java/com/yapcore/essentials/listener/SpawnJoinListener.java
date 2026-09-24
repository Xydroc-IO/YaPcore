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

import java.util.concurrent.atomic.AtomicInteger;

/** Optional hub-style join: always land at this server's /setspawn. */
public final class SpawnJoinListener implements Listener {

    private static final long FIRST_DELAY_TICKS = 20L;
    private static final long RETRY_DELAY_TICKS = 20L;
    private static final int MAX_ATTEMPTS = 5;

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
        if (!liveConfig().spawnTeleportOnJoin()) {
            return;
        }
        Player player = event.getPlayer();
        AtomicInteger attempt = new AtomicInteger(0);
        schedule(player, attempt);
    }

    private void schedule(Player player, AtomicInteger attempt) {
        long delay = attempt.get() == 0 ? FIRST_DELAY_TICKS : RETRY_DELAY_TICKS;
        YapSched.entityLater(plugin, player, () -> run(player, attempt), delay);
    }

    private void run(Player player, AtomicInteger attempt) {
        if (!player.isOnline()) {
            return;
        }
        if (!liveConfig().spawnTeleportOnJoin()) {
            return;
        }
        int n = attempt.incrementAndGet();
        Location spawn = spawnStore.spawn();
        if (spawn == null || spawn.getWorld() == null) {
            if (n < MAX_ATTEMPTS) {
                schedule(player, attempt);
            }
            return;
        }
        TeleportHelper.teleport(plugin, player, spawn);
    }

    private EssentialsConfig liveConfig() {
        if (plugin instanceof com.yapcore.essentials.EssentialsPlugin ess) {
            EssentialsConfig live = ess.essentialsConfig();
            if (live != null) {
                return live;
            }
        }
        return config;
    }
}
