package com.yapcore.disasters;

import com.yapcore.sched.YapSched;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/** Folia-safe sky weather (must run on the global region). */
public final class SkyWeather {

    private SkyWeather() {
    }

    public static void apply(Plugin plugin, World world, DisasterType type, int durationSeconds) {
        if (plugin == null || world == null || type == null) {
            return;
        }
        int ticks = Math.max(20, durationSeconds * 20);
        YapSched.global(plugin, () -> applySync(world, type, ticks));
    }

    public static void setCycle(Plugin plugin, World world, boolean enabled) {
        if (plugin == null || world == null) {
            return;
        }
        YapSched.global(plugin, () -> world.setGameRule(GameRule.DO_WEATHER_CYCLE, enabled));
    }

    public static String describe(World world) {
        if (world == null) {
            return "unknown";
        }
        boolean cycle = Boolean.TRUE.equals(world.getGameRuleValue(GameRule.DO_WEATHER_CYCLE));
        String state = world.isThundering() ? "thunder" : world.hasStorm() ? "rain" : "clear";
        return state + (cycle ? "" : " (cycle locked)");
    }

    private static void applySync(World world, DisasterType type, int ticks) {
        switch (type) {
            case CLEAR, DROUGHT, EARTHQUAKE, METEOR -> {
                if (type == DisasterType.CLEAR || type == DisasterType.DROUGHT) {
                    world.setStorm(false);
                    world.setThundering(false);
                    world.setClearWeatherDuration(ticks);
                }
            }
            case RAIN, BLIZZARD -> {
                world.setStorm(true);
                world.setThundering(false);
                world.setWeatherDuration(ticks);
            }
            case THUNDER, HURRICANE, TORNADO, VOLCANO, TSUNAMI -> {
                world.setStorm(true);
                world.setThundering(true);
                world.setWeatherDuration(ticks);
                world.setThunderDuration(ticks);
            }
        }
    }
}
