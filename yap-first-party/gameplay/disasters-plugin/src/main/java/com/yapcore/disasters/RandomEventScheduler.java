package com.yapcore.disasters;

import com.yapcore.sched.YapSched;
import com.yapcore.sched.YapTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/** Phase 4 — weighted random disasters with pre-warnings. */
public final class RandomEventScheduler {

    private final DisastersPlugin plugin;
    private final Random random = new Random();
    private final AtomicLong nextAtMs = new AtomicLong(0L);
    private YapTask tickTask;
    private boolean runtimeEnabled;

    public RandomEventScheduler(DisastersPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        stop();
        runtimeEnabled = plugin.config().randomEnabled();
        if (runtimeEnabled && plugin.config().enabled()) {
            scheduleNext(plugin.config().randomMinIntervalSeconds());
            tickTask = YapSched.globalTimer(plugin, this::tick, 40L, 40L);
        }
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    public void shutdown() {
        stop();
    }

    public boolean runtimeEnabled() {
        return runtimeEnabled;
    }

    public void setRuntimeEnabled(boolean enabled) {
        runtimeEnabled = enabled;
        if (enabled) {
            if (tickTask == null) {
                scheduleNext(plugin.config().randomMinIntervalSeconds());
                tickTask = YapSched.globalTimer(plugin, this::tick, 40L, 40L);
            }
        } else {
            stop();
            nextAtMs.set(0L);
        }
    }

    public String statusLine() {
        if (!plugin.config().enabled()) {
            return "plugin disabled";
        }
        if (!runtimeEnabled) {
            return "random off";
        }
        long next = nextAtMs.get();
        if (next <= 0L) {
            return "random on — scheduling…";
        }
        long sec = Math.max(0L, (next - System.currentTimeMillis()) / 1000L);
        return "random on — next check in ~" + sec + "s";
    }

    /** Force a random (or specific) event after the configured warning. */
    public boolean triggerNow(World world, DisasterType forcedType) {
        if (world == null || !plugin.config().enabled() || !plugin.config().worldAllowed(world.getName())) {
            return false;
        }
        if (plugin.manager().isActive(world) || plugin.warnings().hasPending(world)) {
            return false;
        }
        DisasterType type = forcedType != null ? forcedType : pickType();
        if (type == null || !plugin.config().typeEnabled(type)) {
            return false;
        }
        Location focus = focusFor(world, type);
        int warn = plugin.config().warningSeconds();
        int duration = plugin.config().randomDurationSeconds();
        plugin.warnings().warnThen(world, type, warn, w -> {
            plugin.manager().start(w, type, duration, focus);
            scheduleNext(0);
        });
        return true;
    }

    private void tick() {
        if (!runtimeEnabled || !plugin.config().enabled() || !plugin.config().randomEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        long next = nextAtMs.get();
        if (next <= 0L) {
            scheduleNext(0);
            return;
        }
        if (now < next) {
            return;
        }
        World world = pickWorld();
        if (world == null) {
            scheduleNext(plugin.config().randomMinIntervalSeconds() / 2);
            return;
        }
        if (plugin.manager().isActive(world) || plugin.warnings().hasPending(world)) {
            scheduleNext(60);
            return;
        }
        DisasterType type = pickType();
        if (type == null) {
            scheduleNext(plugin.config().randomMinIntervalSeconds());
            return;
        }
        Location focus = focusFor(world, type);
        int warn = plugin.config().warningSeconds();
        int duration = plugin.config().randomDurationSeconds();
        plugin.warnings().warnThen(world, type, warn, w -> {
            plugin.manager().start(w, type, duration, focus);
            scheduleNext(0);
        });
        // Hold next until after this event's warning+duration roughly finishes.
        scheduleNext(warn + duration + plugin.config().randomMinIntervalSeconds() / 4);
    }

    private void scheduleNext(int minExtraSeconds) {
        int min = Math.max(30, plugin.config().randomMinIntervalSeconds());
        int max = Math.max(min, plugin.config().randomMaxIntervalSeconds());
        int span = max - min;
        int roll = min + (span <= 0 ? 0 : random.nextInt(span + 1));
        int delay = Math.max(minExtraSeconds, roll);
        nextAtMs.set(System.currentTimeMillis() + delay * 1000L);
    }

    private World pickWorld() {
        List<World> candidates = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            if (!plugin.config().worldAllowed(world.getName())) {
                continue;
            }
            if (plugin.config().randomRequirePlayers() && world.getPlayers().isEmpty()) {
                continue;
            }
            candidates.add(world);
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    private DisasterType pickType() {
        Map<DisasterType, Integer> weights = plugin.config().randomWeights();
        int total = 0;
        for (Map.Entry<DisasterType, Integer> e : weights.entrySet()) {
            if (e.getKey() == null || e.getKey() == DisasterType.CLEAR || e.getKey() == DisasterType.RAIN) {
                continue;
            }
            if (!plugin.config().typeEnabled(e.getKey())) {
                continue;
            }
            total += Math.max(0, e.getValue());
        }
        if (total <= 0) {
            return null;
        }
        int roll = random.nextInt(total);
        int acc = 0;
        for (Map.Entry<DisasterType, Integer> e : weights.entrySet()) {
            DisasterType type = e.getKey();
            if (type == null || type == DisasterType.CLEAR || type == DisasterType.RAIN) {
                continue;
            }
            if (!plugin.config().typeEnabled(type)) {
                continue;
            }
            int w = Math.max(0, e.getValue());
            acc += w;
            if (roll < acc) {
                return type;
            }
        }
        return null;
    }

    private Location focusFor(World world, DisasterType type) {
        if (type == DisasterType.VOLCANO) {
            return plugin.volcanoSites().randomActiveInWorld(world)
                    .map(VolcanoSite::toLocation)
                    .orElseGet(() -> playerOrSpawn(world));
        }
        return playerOrSpawn(world);
    }

    private static Location playerOrSpawn(World world) {
        for (Player player : world.getPlayers()) {
            return player.getLocation().clone();
        }
        return world.getSpawnLocation().clone();
    }

    public static DisasterType parseForceType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if ("random".equalsIgnoreCase(raw) || "any".equalsIgnoreCase(raw)) {
            return null;
        }
        return DisasterType.parse(raw.toLowerCase(Locale.ROOT));
    }
}
