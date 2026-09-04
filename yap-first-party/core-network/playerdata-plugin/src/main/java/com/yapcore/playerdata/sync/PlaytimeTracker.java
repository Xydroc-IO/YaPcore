package com.yapcore.playerdata.sync;

import com.yapcore.playerdata.db.PlayerRepository;
import com.yapcore.sched.YapSched;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Tracks lifetime play minutes: persisted {@code players.play_minutes} + open session.
 */
public final class PlaytimeTracker implements Listener {

    private final JavaPlugin plugin;
    private final PlayerRepository repository;
    private final Map<UUID, Long> sessionStartMs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cachedMinutes = new ConcurrentHashMap<>();

    public PlaytimeTracker(JavaPlugin plugin, PlayerRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    public long playMinutes(UUID uuid) {
        if (uuid == null) {
            return 0L;
        }
        long stored = cachedMinutes.getOrDefault(uuid, 0L);
        Long start = sessionStartMs.get(uuid);
        if (start == null) {
            return stored;
        }
        long session = Math.max(0L, (System.currentTimeMillis() - start) / 60_000L);
        return stored + session;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        sessionStartMs.put(uuid, System.currentTimeMillis());
        YapSched.async(plugin, () -> {
            try {
                repository.ensure(uuid, player.getName());
                long minutes = repository.getPlayMinutes(uuid);
                cachedMinutes.put(uuid, minutes);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "playtime load failed for " + uuid, e);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        flushSession(event.getPlayer().getUniqueId());
    }

    public void flushAllOnline() {
        for (UUID uuid : sessionStartMs.keySet()) {
            flushSession(uuid);
        }
    }

    private void flushSession(UUID uuid) {
        Long start = sessionStartMs.remove(uuid);
        if (start == null) {
            cachedMinutes.remove(uuid);
            return;
        }
        long sessionMinutes = Math.max(0L, (System.currentTimeMillis() - start) / 60_000L);
        if (sessionMinutes <= 0) {
            cachedMinutes.remove(uuid);
            return;
        }
        YapSched.async(plugin, () -> {
            try {
                long total = repository.addPlayMinutes(uuid, sessionMinutes);
                cachedMinutes.put(uuid, total);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "playtime save failed for " + uuid, e);
            } finally {
                cachedMinutes.remove(uuid);
            }
        });
    }
}
