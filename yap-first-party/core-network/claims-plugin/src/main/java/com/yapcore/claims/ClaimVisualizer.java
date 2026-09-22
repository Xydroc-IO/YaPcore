package com.yapcore.claims;

import com.yapcore.sched.YapSched;
import com.yapcore.sched.YapTask;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;

/** Particle outline of claim borders (brief flash or continuous toggle). */
public final class ClaimVisualizer {
    private ClaimVisualizer() {
    }

    public static void show(JavaPlugin plugin, Player player, Claim claim, int seconds) {
        if (player == null || claim == null || !player.getWorld().getName().equals(claim.world())) {
            return;
        }
        final int[] left = {Math.max(2, seconds * 2)}; // every 10 ticks
        final YapTask[] handle = new YapTask[1];
        handle[0] = YapSched.globalTimer(plugin, () -> YapSched.entity(plugin, player, () -> {
            if (!player.isOnline() || left[0]-- <= 0) {
                handle[0].cancel();
                return;
            }
            if (!player.getWorld().getName().equals(claim.world())) {
                handle[0].cancel();
                return;
            }
            draw(player, claim, Color.LIME);
        }), 1L, 10L);
    }

    /** One frame for continuous border view (call from the toggle ticker). */
    public static void drawFrame(Player player, Collection<Claim> claims) {
        if (player == null || claims == null || claims.isEmpty()) {
            return;
        }
        String world = player.getWorld().getName();
        for (Claim claim : claims) {
            if (claim != null && world.equals(claim.world())) {
                draw(player, claim, Color.LIME);
            }
        }
    }

    private static void draw(Player player, Claim claim, Color color) {
        World world = player.getWorld();
        double y = player.getLocation().getY() + 1;
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.2f);
        int step = claim.width() >= 32 || claim.length() >= 32 ? 2 : 1;
        for (int x = claim.minX(); x <= claim.maxX(); x += step) {
            spawn(player, world, x + 0.5, y, claim.minZ() + 0.5, dust);
            spawn(player, world, x + 0.5, y, claim.maxZ() + 0.5, dust);
        }
        for (int z = claim.minZ(); z <= claim.maxZ(); z += step) {
            spawn(player, world, claim.minX() + 0.5, y, z + 0.5, dust);
            spawn(player, world, claim.maxX() + 0.5, y, z + 0.5, dust);
        }
        // corners always (step may skip them)
        spawn(player, world, claim.minX() + 0.5, y, claim.minZ() + 0.5, dust);
        spawn(player, world, claim.maxX() + 0.5, y, claim.minZ() + 0.5, dust);
        spawn(player, world, claim.minX() + 0.5, y, claim.maxZ() + 0.5, dust);
        spawn(player, world, claim.maxX() + 0.5, y, claim.maxZ() + 0.5, dust);
    }

    private static void spawn(Player player, World world, double x, double y, double z,
                              Particle.DustOptions dust) {
        player.spawnParticle(Particle.DUST, new Location(world, x, y, z), 1, 0, 0, 0, 0, dust);
    }
}
