package com.yapcore.config;

/**
 * Who owns the Minecraft game simulation.
 *
 * <ul>
 *   <li>{@link #FOLIA} — product path: Folia owns world/player tick (regionized)</li>
 *   <li>{@link #PAPER} — legacy: Paper game (+ optional Phase 3 spatial tick)</li>
 *   <li>{@link #NATIVE} — experimental YapEngine flat world</li>
 *   <li>{@link #MOJANG} — legacy Mojang dedicated-server kernel</li>
 * </ul>
 */
public enum GameAuthority {
    FOLIA,
    PAPER,
    NATIVE,
    MOJANG;

    public static GameAuthority parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return FOLIA;
        }
        return switch (raw.trim().toLowerCase()) {
            case "folia" -> FOLIA;
            case "native", "yap", "yapengine" -> NATIVE;
            case "mojang", "vanilla", "kernel" -> MOJANG;
            case "paper" -> PAPER;
            default -> FOLIA;
        };
    }
}
