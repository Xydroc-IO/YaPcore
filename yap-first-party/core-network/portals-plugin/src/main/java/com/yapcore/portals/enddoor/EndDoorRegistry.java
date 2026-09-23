package com.yapcore.portals.enddoor;

import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory active End doors. Walk-in uses AABB checks — no Folia TileState reads on move.
 */
public final class EndDoorRegistry {

    private static final ConcurrentHashMap<String, EndDoorStructure.Frame> ACTIVE = new ConcurrentHashMap<>();

    private EndDoorRegistry() {
    }

    public static String key(EndDoorStructure.Frame frame) {
        return frame.world().getUID() + "|" + frame.axis().name() + "|"
                + frame.minAlong() + "|" + frame.minY() + "|" + frame.fixed() + "|"
                + frame.sizeAlong() + "|" + frame.height();
    }

    public static void register(EndDoorStructure.Frame frame) {
        ACTIVE.put(key(frame), frame);
    }

    public static void unregister(EndDoorStructure.Frame frame) {
        ACTIVE.remove(key(frame));
    }

    public static void clear() {
        ACTIVE.clear();
    }

    public static Collection<EndDoorStructure.Frame> all() {
        return List.copyOf(ACTIVE.values());
    }

    public static Optional<EndDoorStructure.Frame> at(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return Optional.empty();
        }
        World world = loc.getWorld();
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();
        for (EndDoorStructure.Frame frame : ACTIVE.values()) {
            if (!frame.world().equals(world)) {
                continue;
            }
            if (inside(frame, x, y, z)) {
                return Optional.of(frame);
            }
        }
        return Optional.empty();
    }

    /** Expanded volume so walking a block in front of the thin plane still counts. */
    static boolean inside(EndDoorStructure.Frame frame, double x, double y, double z) {
        double y0 = frame.minY() - 0.2;
        double y1 = frame.maxY() + 1.2;
        if (y < y0 || y > y1) {
            return false;
        }
        double along0 = frame.minAlong() - 0.35;
        double along1 = frame.maxAlong() + 1.35;
        double depth = 1.75; // blocks in front/behind the portal plane
        if (frame.axis() == Axis.X) {
            return x >= along0 && x <= along1
                    && Math.abs(z - (frame.fixed() + 0.5)) <= depth;
        }
        return z >= along0 && z <= along1
                && Math.abs(x - (frame.fixed() + 0.5)) <= depth;
    }
}
