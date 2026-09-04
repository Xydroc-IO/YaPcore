package com.yapcore.disasters;

import com.yapcore.sched.YapSched;
import com.yapcore.sched.YapTask;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Countdown warning (bossbar + title + sound) before a disaster starts. */
public final class WarningService {

    private final DisastersPlugin plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public WarningService(DisastersPlugin plugin) {
        this.plugin = plugin;
    }

    public int pendingCount() {
        return pending.size();
    }

    public boolean hasPending(World world) {
        return world != null && pending.containsKey(world.getUID());
    }

    public void cancel(World world) {
        if (world == null) {
            return;
        }
        Pending p = pending.remove(world.getUID());
        if (p != null) {
            p.cancel();
        }
    }

    public void shutdown() {
        for (UUID id : new ArrayList<>(pending.keySet())) {
            Pending p = pending.remove(id);
            if (p != null) {
                p.cancel();
            }
        }
    }

    /**
     * Warn players in the world, then run {@code onReady}.
     * If warningSeconds &lt;= 0, runs immediately.
     */
    public void warnThen(World world, DisasterType type, int warningSeconds, Consumer<World> onReady) {
        if (world == null || type == null || onReady == null) {
            return;
        }
        cancel(world);
        int seconds = Math.max(0, warningSeconds);
        if (seconds <= 0 || !plugin.config().warningsEnabled()) {
            onReady.accept(world);
            return;
        }

        BossBar bar = BossBar.bossBar(
                Component.text("Incoming " + type.configKey() + " — " + seconds + "s", NamedTextColor.RED),
                1.0f,
                BossBar.Color.RED,
                BossBar.Overlay.NOTCHED_10);
        Pending effect = new Pending(world.getUID(), type, bar, null);
        pending.put(world.getUID(), effect);

        announce(world, type, seconds);
        showBar(world, bar);

        final int[] left = {seconds};
        effect.task = YapSched.globalTimer(plugin, () -> {
            Pending current = pending.get(world.getUID());
            if (current != effect) {
                return;
            }
            left[0]--;
            if (left[0] <= 0) {
                pending.remove(world.getUID(), effect);
                // Cancel the repeating timer — otherwise it keeps firing forever.
                effect.cancel();
                Bukkit.broadcastMessage("§c§l" + pretty(type) + " §chas begun in §f" + world.getName() + "§c!");
                onReady.accept(world);
                return;
            }
            float progress = left[0] / (float) seconds;
            bar.name(Component.text("Incoming " + type.configKey() + " — " + left[0] + "s", NamedTextColor.RED));
            bar.progress(Math.max(0.01f, progress));
            showBar(world, bar);
            if (left[0] <= 5 || left[0] % 10 == 0) {
                pulse(world, type, left[0]);
            }
        }, 20L, 20L);
    }

    private void announce(World world, DisasterType type, int seconds) {
        String pretty = pretty(type);
        Bukkit.broadcastMessage("§e⚠ §c" + pretty + " §ewarning in §f" + world.getName()
                + " §e— §f" + seconds + "s§e.");
        Title title = Title.title(
                Component.text(pretty, NamedTextColor.RED),
                Component.text("arriving in " + seconds + "s", NamedTextColor.GOLD),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(400)));
        for (Player player : world.getPlayers()) {
            YapSched.entity(plugin, player, () -> {
                if (!player.isOnline() || player.getWorld() != world) {
                    return;
                }
                player.showTitle(title);
                player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 0.5f);
                player.playSound(player.getLocation(), Sound.EVENT_RAID_HORN, 0.35f, 0.8f);
            });
        }
    }

    private void pulse(World world, DisasterType type, int left) {
        for (Player player : world.getPlayers()) {
            YapSched.entity(plugin, player, () -> {
                if (!player.isOnline() || player.getWorld() != world) {
                    return;
                }
                player.sendActionBar(Component.text("⚠ " + pretty(type) + " in " + left + "s",
                        NamedTextColor.GOLD));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 0.7f);
            });
        }
    }

    private void showBar(World world, BossBar bar) {
        for (Player player : world.getPlayers()) {
            YapSched.entity(plugin, player, () -> {
                if (player.isOnline() && player.getWorld() == world) {
                    player.showBossBar(bar);
                }
            });
        }
        // Hide from players who left the world
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld() != world) {
                YapSched.entity(plugin, player, () -> player.hideBossBar(bar));
            }
        }
    }

    private static String pretty(DisasterType type) {
        String key = type.configKey();
        return Character.toUpperCase(key.charAt(0)) + key.substring(1);
    }

    private static final class Pending {
        final UUID worldId;
        final DisasterType type;
        final BossBar bar;
        YapTask task;

        Pending(UUID worldId, DisasterType type, BossBar bar, YapTask task) {
            this.worldId = worldId;
            this.type = type;
            this.bar = bar;
            this.task = task;
        }

        void cancel() {
            cancelBarsOnly();
            if (task != null) {
                task.cancel();
                task = null;
            }
        }

        void cancelBarsOnly() {
            if (bar == null) {
                return;
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.hideBossBar(bar);
            }
        }
    }
}
