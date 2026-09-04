package com.yapcore.disasters;

import java.util.Locale;

/** Staff-triggerable disaster / weather kinds. */
public enum DisasterType {
    CLEAR,
    RAIN,
    THUNDER,
    HURRICANE,
    TORNADO,
    EARTHQUAKE,
    VOLCANO,
    BLIZZARD,
    DROUGHT,
    METEOR,
    TSUNAMI;

    public static DisasterType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "clear", "sun", "sunny" -> CLEAR;
            case "rain", "storm", "downfall" -> RAIN;
            case "thunder", "thunderstorm", "lightning" -> THUNDER;
            case "hurricane", "cyclone", "typhoon" -> HURRICANE;
            case "tornado", "twister", "vortex" -> TORNADO;
            case "earthquake", "quake", "tremor" -> EARTHQUAKE;
            case "volcano", "erupt", "eruption", "lava" -> VOLCANO;
            case "blizzard", "snowstorm", "snow" -> BLIZZARD;
            case "drought", "dry", "heatwave" -> DROUGHT;
            case "meteor", "meteorite", "meteorshower", "shower" -> METEOR;
            case "tsunami", "flood", "tidal", "wave" -> TSUNAMI;
            default -> null;
        };
    }

    public boolean hasFx() {
        return this != CLEAR && this != RAIN;
    }

    public String configKey() {
        return name().toLowerCase(Locale.ROOT);
    }
}
