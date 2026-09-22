package com.yapcore.haze;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Short-lived camera bob + green vignette strength + leaf particles.
 * No permanent movement hijack.
 */
public final class HazeEffect {

    private long endMs;
    private float peakIntensity;

    public void trigger(double intensity, int durationTicks) {
        float i = (float) Mth.clamp(intensity, 0.0, 1.0);
        long durationMs = Math.max(200L, durationTicks * 50L);
        this.peakIntensity = Math.max(this.peakIntensity * remainingFactor(), i);
        this.endMs = System.currentTimeMillis() + durationMs;
    }

    public boolean active() {
        return System.currentTimeMillis() < endMs && peakIntensity > 0.01f;
    }

    public float strength() {
        if (!active()) {
            peakIntensity = 0f;
            return 0f;
        }
        return peakIntensity * remainingFactor();
    }

    private float remainingFactor() {
        long left = endMs - System.currentTimeMillis();
        if (left <= 0) {
            return 0f;
        }
        long total = Math.max(1L, endMs - (endMs - left));
        // Approximate fade over last third
        float frac = Mth.clamp(left / 3000f, 0f, 1f);
        return frac;
    }

    /** Camera pitch/yaw bob in degrees. */
    public float bobDegrees() {
        float s = strength();
        if (s <= 0f) {
            return 0f;
        }
        float t = (System.currentTimeMillis() % 1000L) / 1000f;
        return (float) Math.sin(t * Math.PI * 2.0) * 1.8f * s;
    }

    /** Green overlay alpha 0..1. */
    public float vignetteAlpha() {
        return strength() * 0.35f;
    }

    public void tickParticles(Minecraft minecraft) {
        if (!active() || minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (minecraft.level.getGameTime() % 4L != 0L) {
            return;
        }
        Player player = minecraft.player;
        var rng = java.util.concurrent.ThreadLocalRandom.current();
        double x = player.getX() + (rng.nextDouble() - 0.5) * 1.5;
        double y = player.getEyeY() + (rng.nextDouble() - 0.5) * 0.8;
        double z = player.getZ() + (rng.nextDouble() - 0.5) * 1.5;
        minecraft.level.addParticle(ParticleTypes.HAPPY_VILLAGER, x, y, z, 0, 0.02, 0);
    }
}
