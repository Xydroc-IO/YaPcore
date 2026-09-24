package com.yapcore.portals;

import java.util.Locale;

/**
 * Where a player lands after using a portal.
 * {@link #SPAWN} is the destination spawn; {@link #RTP} is a random safe spot;
 * {@link #HOME} is the player's YaPPlayerData home on that backend;
 * {@link #ISLAND} is their YaPblock island (create-on-first if missing).
 */
public enum PortalArrival {
    SPAWN,
    RTP,
    HOME,
    ISLAND;

    public static PortalArrival parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return SPAWN;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        // home:<name> → HOME (name stored separately on Portal / pending file)
        if (s.startsWith("home:") || s.startsWith("sethome:")) {
            return HOME;
        }
        return switch (s) {
            case "rtp", "wild", "random", "wilderness" -> RTP;
            case "home", "sethome", "bed" -> HOME;
            case "island", "skyblock", "is", "ishome", "island-home" -> ISLAND;
            case "spawn", "hub", "default" -> SPAWN;
            default -> SPAWN;
        };
    }

    public static boolean known(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("home:") || s.startsWith("sethome:")) {
            return true;
        }
        return switch (s) {
            case "rtp", "wild", "random", "wilderness",
                 "home", "sethome", "bed",
                 "island", "skyblock", "is", "ishome", "island-home",
                 "spawn", "hub", "default" -> true;
            default -> false;
        };
    }

    /** Optional home name from {@code home:cabin} style tokens; empty → default {@code home}. */
    public static String homeNameOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return "home";
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("home:")) {
            String name = s.substring(5).trim();
            return name.isEmpty() ? "home" : name;
        }
        if (s.startsWith("sethome:")) {
            String name = s.substring(8).trim();
            return name.isEmpty() ? "home" : name;
        }
        return "home";
    }
}
