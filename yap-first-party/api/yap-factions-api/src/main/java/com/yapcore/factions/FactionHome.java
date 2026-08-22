package com.yapcore.factions;

public record FactionHome(
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
        return new FactionHome(null, 0, 0, 0, 0, 0);
    }
}
