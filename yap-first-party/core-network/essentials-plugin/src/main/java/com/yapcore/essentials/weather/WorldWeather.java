package com.yapcore.essentials.weather;

import com.yapcore.sched.YapSched;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.Locale;

/** Folia-safe basic sky weather (fallback when YaPDisasters is offline). */
public final class WorldWeather {

    public enum Mode {
        CLEAR,
        RAIN,
        THUNDER
    }

    public static final int DEFAULT_SECONDS = 600;
    public static final int LOCK_SECONDS = 7 * 24 * 60 * 60;

    private WorldWeather() {
    }

    public static Mode parseMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "clear", "sun", "sunny" -> Mode.CLEAR;
            case "rain", "storm", "downfall" -> Mode.RAIN;
            case "thunder", "thunderstorm", "lightning" -> Mode.THUNDER;
            default -> null;
        };
    }

    public static void apply(Plugin plugin, World world, Mode mode, int durationSeconds) {
        if (plugin == null || world == null || mode == null) {
            return;
        }
        int ticks = Math.max(20, Math.min(durationSeconds, LOCK_SECONDS) * 20);
        YapSched.global(plugin, () -> {
            switch (mode) {
                case CLEAR -> {
                    world.setStorm(false);
                    world.setThundering(false);
                    world.setClearWeatherDuration(ticks);
                }
                case RAIN -> {
                    world.setStorm(true);
                    world.setThundering(false);
                    world.setWeatherDuration(ticks);
                }
                case THUNDER -> {
                    world.setStorm(true);
                    world.setThundering(true);
                    world.setWeatherDuration(ticks);
                    world.setThunderDuration(ticks);
                }
            }
        });
    }

    public static void setCycle(Plugin plugin, World world, boolean enabled) {
        if (plugin == null || world == null) {
            return;
        }
        YapSched.global(plugin, () -> world.setGameRule(GameRule.DO_WEATHER_CYCLE, enabled));
    }
}
