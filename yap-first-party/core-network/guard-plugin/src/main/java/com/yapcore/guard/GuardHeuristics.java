package com.yapcore.guard;

import org.bukkit.Material;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Pure helpers for YaPGuard heuristics (unit-testable; no Bukkit player required).
 */
public final class GuardHeuristics {

    private GuardHeuristics() {
    }

    /**
     * Solid / slab / stairs / carpet / scaffolding under feet.
     * Avoids {@link Material#isAir()} / registry-heavy calls so unit tests work.
     */
    public static boolean isGroundLike(Material type) {
        if (type == null) {
            return false;
        }
        String name = type.name();
        if ("AIR".equals(name) || "CAVE_AIR".equals(name) || "VOID_AIR".equals(name)) {
            return false;
        }
        if ("SCAFFOLDING".equals(name)) {
            return true;
        }
        if (name.endsWith("_SLAB") || name.endsWith("_STAIRS") || name.endsWith("_CARPET")) {
            return true;
        }
        if (name.contains("WATER") || name.contains("LAVA") || "BUBBLE_COLUMN".equals(name)) {
            return false;
        }
        try {
            return type.isSolid();
        } catch (Throwable t) {
            // Paper RegistryAccess missing in JVM unit tests
            return true;
        }
    }

    /**
     * Max horizontal/vertical travel allowed for one sample window (blocks per tick).
     */
    public static double speedAllowedBlocksPerTick(
            double maxBlocksPerTick, double speedSensitivity, boolean sprinting, boolean gliding) {
        double allowed = maxBlocksPerTick;
        if (sprinting) {
            allowed *= 1.35;
        }
        if (gliding) {
            allowed *= 2.5;
        }
        allowed *= (0.5 + clamp01(speedSensitivity));
        return allowed;
    }

    /**
     * Whether to record a violation for this sample.
     * When {@code sampleRandomly} is false, always flag when the check failed.
     * When true, flag with probability = sensitivity (legacy soft mode).
     */
    public static boolean shouldFlagSample(boolean checkFailed, double sensitivity, boolean sampleRandomly) {
        if (!checkFailed) {
            return false;
        }
        if (!sampleRandomly) {
            return true;
        }
        return ThreadLocalRandom.current().nextDouble() <= clamp01(sensitivity);
    }

    public static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
