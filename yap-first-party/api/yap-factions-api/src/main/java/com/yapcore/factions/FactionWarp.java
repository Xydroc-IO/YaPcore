package com.yapcore.factions;

/** Named faction warp (shared by members). */
public record FactionWarp(
        long factionId,
        String name,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch) {
}
