package com.yapcore.factions;

/**
 * Shared faction base home. Distinct from personal {@code /sethome} (per-player).
 *
 * @param serverId fleet backend id (e.g. {@code survival}); blank means same-server / legacy
 */
public record FactionHome(
        String serverId,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch) {

    public boolean isSet() {
        return world != null && !world.isBlank();
    }

    public static FactionHome unset() {
        return new FactionHome(null, null, 0, 0, 0, 0, 0);
    }

    /** Legacy constructor (no server id) — home is only usable on the world that is loaded here. */
    public static FactionHome of(String world, double x, double y, double z, float yaw, float pitch) {
        return new FactionHome(null, world, x, y, z, yaw, pitch);
    }
}
