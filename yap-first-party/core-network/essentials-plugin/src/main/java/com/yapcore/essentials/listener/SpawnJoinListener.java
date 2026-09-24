package com.yapcore.essentials.listener;

import com.yapcore.essentials.EssentialsConfig;
import com.yapcore.essentials.store.SpawnStore;
import com.yapcore.essentials.util.JoinGamemode;
import com.yapcore.essentials.util.TeleportHelper;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Optional hub-style join: land at /setspawn and force join gamemode. */
public final class SpawnJoinListener implements Listener {

    private static final long FIRST_DELAY_TICKS = 40L;
    private static final long RETRY_DELAY_TICKS = 20L;
    private static final int MAX_ATTEMPTS = 8;
    /**
     * Only skip when already standing on the pad. A wider radius left players on
     * nearby portal pads after failover / reconnect.
     */
    private static final double NEAR_SPAWN_BLOCKS = 2.0;

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
        EssentialsConfig live = liveConfig();
        if (!live.spawnTeleportOnJoin() && live.spawnForceGamemode().isEmpty()) {
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
        EssentialsConfig live = liveConfig();
        boolean teleport = live.spawnTeleportOnJoin();
        boolean forceGm = live.spawnForceGamemode().isPresent();
        if (!teleport && !forceGm) {
            return;
        }
        int n = attempt.incrementAndGet();
        // Wait for YaPPlayerData so last-logout restore cannot overwrite /setspawn or gamemode.
        if (!playerDataReady(player) && n < MAX_ATTEMPTS) {
            schedule(player, attempt);
            return;
        }
        if (forceGm) {
            JoinGamemode.apply(player, live.spawnForceGamemode());
        }
        if (!teleport) {
            return;
        }
        Location spawn = spawnStore.spawn();
        if (spawn == null || spawn.getWorld() == null) {
            if (n < MAX_ATTEMPTS) {
                schedule(player, attempt);
            }
            return;
        }
        Location here = player.getLocation();
        if (here.getWorld() != null && here.getWorld().equals(spawn.getWorld())
                && here.distanceSquared(spawn) <= NEAR_SPAWN_BLOCKS * NEAR_SPAWN_BLOCKS) {
            return;
        }
        TeleportHelper.teleport(plugin, player, spawn);
    }

    /** Prefer waiting until YaPPlayerData has applied the profile. */
    private static boolean playerDataReady(Player player) {
        Plugin pd = Bukkit.getPluginManager().getPlugin("YaPPlayerData");
        if (pd == null || !pd.isEnabled()) {
            return true;
        }
        try {
            Object sync = pd.getClass().getMethod("sync").invoke(pd);
            if (sync == null) {
                return true;
            }
            Object ready = sync.getClass().getMethod("isReady", UUID.class)
                    .invoke(sync, player.getUniqueId());
            return Boolean.TRUE.equals(ready);
        } catch (ReflectiveOperationException e) {
            return true;
        }
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
