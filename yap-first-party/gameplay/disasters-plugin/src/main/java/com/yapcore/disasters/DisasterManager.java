package com.yapcore.disasters;

import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Runs Folia-safe disaster FX for one active event per world. */
public final class DisasterManager {

    private final DisastersPlugin plugin;
    private final Map<UUID, DisasterActive> active = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private final DisasterFx fx;

    public DisasterManager(DisastersPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin);
        this.fx = new DisasterFx(plugin, random);
    }

    public void shutdown() {
        for (UUID id : active.keySet()) {
            stop(id, false);
        }
        active.clear();
    }

    public void stop(World world) {
        if (world != null) {
            stop(world.getUID(), true);
        }
    }

    public void stop(UUID worldId, boolean announce) {
        DisasterActive prev = active.remove(worldId);
        if (prev == null) {
            return;
        }
        if (prev.task != null) {
            prev.task.cancel();
        }
        if (prev.endTask != null) {
            prev.endTask.cancel();
        }
        prev.cancelUndos();
        if (announce && plugin.config().broadcastEnd()) {
            World w = Bukkit.getWorld(worldId);
            if (w != null) {
                Bukkit.broadcastMessage("§7Disaster ended in §f" + w.getName() + "§7.");
            }
        }
    }

    public String describeActive(World world) {
        if (world == null) {
            return "none";
        }
        DisasterActive a = active.get(world.getUID());
        return a == null ? "none" : a.type.configKey();
    }

    /** One-line status for console / dashboard. */
    public String statusReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("random=").append(plugin.randomEvents().statusLine());
        sb.append(" sites=").append(plugin.volcanoSites().all().size());
        sb.append(" grief=").append(plugin.config().grief());
        sb.append(" pending-warn=").append(plugin.warnings().pendingCount());
        sb.append(" undos=").append(undoTaskCount());
        boolean any = false;
        for (World world : Bukkit.getWorlds()) {
            DisasterActive a = active.get(world.getUID());
            if (a == null) {
                continue;
            }
            if (!any) {
                sb.append(" active=");
                any = true;
            } else {
                sb.append(',');
            }
            long left = Math.max(0L, (a.endsAtMs - System.currentTimeMillis()) / 1000L);
            sb.append(world.getName()).append(':').append(a.type.configKey()).append('(').append(left).append("s)");
        }
        if (!any) {
            sb.append(" active=none");
        }
        return sb.toString();
    }

    public int activeCount() {
        return active.size();
    }

    public int undoTaskCount() {
        int n = 0;
        for (DisasterActive a : active.values()) {
            n += a.undoCount();
        }
        return n;
    }

    public boolean isActive(World world) {
        return world != null && active.containsKey(world.getUID());
    }

    public boolean isActiveType(World world, DisasterType type) {
        if (world == null || type == null) {
            return false;
        }
        DisasterActive a = active.get(world.getUID());
        return a != null && a.type == type;
    }

    public boolean start(World world, DisasterType type, int durationSeconds, Location focus) {
        DisastersConfig cfg = plugin.config();
        if (!cfg.enabled() || !cfg.typeEnabled(type) || !cfg.worldAllowed(world.getName())) {
            return false;
        }
        plugin.warnings().cancel(world);
        stop(world.getUID(), false);

        SkyWeather.apply(plugin, world, type, durationSeconds);
        if (!type.hasFx()) {
            if (cfg.broadcastStart()) {
                Bukkit.broadcastMessage("§bWeather §f" + type.configKey()
                        + " §bin §f" + world.getName() + "§b.");
            }
            return true;
        }

        Location anchor = focus != null ? focus.clone() : defaultAnchor(world);
        if (type == DisasterType.VOLCANO) {
            Location siteFocus = plugin.volcanoSites().resolveVolcanoFocus(world, anchor);
            if (siteFocus != null) {
                anchor = siteFocus.clone();
            }
        }
        DisasterActive effect = new DisasterActive(type, System.currentTimeMillis() + durationSeconds * 1000L, anchor);
        active.put(world.getUID(), effect);

        long period = cfg.periodTicks(type, defaultPeriod(type));
        long durationTicks = Math.max(20L, (long) durationSeconds * 20L);
        effect.task = YapSched.globalTimer(plugin, () -> tick(world.getUID()), period, period);
        effect.endTask = YapSched.globalLater(plugin, () -> {
            if (active.get(world.getUID()) == effect) {
                stop(world.getUID(), true);
            }
        }, durationTicks);

        if (cfg.broadcastStart()) {
            Bukkit.broadcastMessage("§cDisaster §f" + type.configKey()
                    + " §cstarted in §f" + world.getName()
                    + " §c(" + durationSeconds + "s)§c.");
        }
        return true;
    }

    private static long defaultPeriod(DisasterType type) {
        return switch (type) {
            case THUNDER -> 35L;
            case HURRICANE -> 12L;
            case TORNADO -> 4L;
            case EARTHQUAKE -> 8L;
            case VOLCANO -> 10L;
            case BLIZZARD -> 8L;
            case DROUGHT -> 20L;
            case METEOR -> 14L;
            case TSUNAMI -> 8L;
            default -> 40L;
        };
    }

    private static Location defaultAnchor(World world) {
        for (Player player : world.getPlayers()) {
            return player.getLocation().clone();
        }
        return world.getSpawnLocation().clone();
    }

    private void tick(UUID worldId) {
        DisasterActive effect = active.get(worldId);
        if (effect == null) {
            return;
        }
        if (System.currentTimeMillis() > effect.endsAtMs) {
            stop(worldId, true);
            return;
        }
        World world = Bukkit.getWorld(worldId);
        if (world == null) {
            stop(worldId, false);
            return;
        }
        fx.tick(effect, world);
    }
}
