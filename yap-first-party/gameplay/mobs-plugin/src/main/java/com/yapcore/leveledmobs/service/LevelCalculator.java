package com.yapcore.leveledmobs.service;

import com.yapcore.leveledmobs.LeveledMobsConfig;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;

import java.util.concurrent.ThreadLocalRandom;

public final class LevelCalculator {

    private final LeveledMobsConfig config;

    public LevelCalculator(LeveledMobsConfig config) {
        this.config = config;
    }

    public int calculate(LivingEntity entity) {
        return switch (config.strategy()) {
            case RANDOM -> randomLevel();
            case DISTANCE_FROM_SPAWN -> distanceLevel(entity.getLocation());
        };
    }

    public int distanceLevel(Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            return config.minLevel();
        }
        Location spawn = world.getSpawnLocation();
        double dx = loc.getX() - spawn.getX();
        double dz = loc.getZ() - spawn.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        int level = config.minLevel() + (int) Math.floor(dist / config.blocksPerLevel());
        return config.clamp(level);
    }

    public int randomLevel() {
        int min = config.minLevel();
        int max = config.maxLevel();
        if (max <= min) {
            return min;
        }
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }
}
