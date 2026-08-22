package com.yapcore.guilds;

public record GuildHome(
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch) {

    public boolean isSet() {
        return world != null && !world.isBlank();
    }

    public static GuildHome unset() {
        return new GuildHome(null, 0, 0, 0, 0, 0);
    }
}
