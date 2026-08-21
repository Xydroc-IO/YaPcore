package com.yapcore.config;

/**
 * Who owns the Minecraft game simulation.
 *
 * <ul>
 *   <li>{@link #PAPER} — product path: Paper game → YapEngine Phase 3 tick</li>
 *   <li>{@link #NATIVE} — experimental YapEngine flat world</li>
 *   <li>{@link #MOJANG} — legacy Mojang dedicated-server kernel</li>
 * </ul>
 */
public enum GameAuthority {
    PAPER,
    NATIVE,
    MOJANG;

    public static GameAuthority parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return PAPER;
        }
        return switch (raw.trim().toLowerCase()) {
            case "native", "yap", "yapengine" -> NATIVE;
            case "mojang", "vanilla", "kernel" -> MOJANG;
            case "paper" -> PAPER;
            default -> PAPER;
        };
    }
}
