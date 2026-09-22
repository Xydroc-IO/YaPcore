package com.yapcore.skills.listener;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Folia-safe travel distance: prefers the larger of vanilla statistic deltas and
 * location distance so XP still grants when stats lag or fail to tick.
 */
final class TravelXpTracker {

    private record LocSnap(String world, double x, double y, double z) {
        static LocSnap of(Location loc) {
            var world = loc.getWorld();
            return new LocSnap(world == null ? "" : world.getName(), loc.getX(), loc.getY(), loc.getZ());
        }

        double distanceTo(Location loc) {
            var world = loc.getWorld();
            if (world == null || !world.getName().equals(world())) {
                return 0;
            }
            double dx = loc.getX() - x;
            double dy = loc.getY() - y;
            double dz = loc.getZ() - z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    private final Map<UUID, Long> baselineCm = new ConcurrentHashMap<>();
    private final Map<UUID, LocSnap> lastLoc = new ConcurrentHashMap<>();

    void reset(Player player, long cm) {
        UUID id = player.getUniqueId();
        baselineCm.put(id, cm);
        lastLoc.put(id, LocSnap.of(player.getLocation()));
    }

    void clear(UUID id) {
        if (id == null) {
            return;
        }
        baselineCm.remove(id);
        lastLoc.remove(id);
    }

    /**
     * Always advances baselines. Returns blocks to grant when {@code eligible}, else 0.
     * {@code maxBlocks} is the per-sample cap (already scaled for the sample period).
     */
    double consumeBlocks(Player player, long cmNow, boolean eligible, double maxBlocks) {
        UUID id = player.getUniqueId();
        Location loc = player.getLocation();
        Long prevCm = baselineCm.put(id, cmNow);
        LocSnap prevLoc = lastLoc.put(id, LocSnap.of(loc));
        if (!eligible) {
            return 0;
        }
        double fromStat = 0;
        if (prevCm != null && cmNow > prevCm) {
            fromStat = (cmNow - prevCm) / 100.0;
        }
        double fromLoc = prevLoc == null ? 0 : prevLoc.distanceTo(loc);
        double blocks = Math.max(fromStat, fromLoc);
        if (blocks < 0.05) {
            return 0;
        }
        double cap = Math.max(0.1, maxBlocks);
        return Math.min(blocks, cap);
    }
}
