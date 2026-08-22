package com.yapcore.playerdata.claims;

import com.yapcore.sched.YapSched;
import com.yapcore.sched.YapTask;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Particle outline of claim borders. */
public final class ClaimVisualizer {
    private ClaimVisualizer() {
    }

    public static void show(JavaPlugin plugin, Player player, Claim claim, int seconds) {
        if (!player.getWorld().getName().equals(claim.world())) {
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
            draw(player, claim);
        }), 1L, 10L);
    }

    private static void draw(Player player, Claim claim) {
        World world = player.getWorld();
        double y = player.getLocation().getY() + 1;
        Particle.DustOptions dust = new Particle.DustOptions(Color.LIME, 1.2f);
        for (int x = claim.minX(); x <= claim.maxX(); x++) {
            spawn(player, world, x + 0.5, y, claim.minZ() + 0.5, dust);
            spawn(player, world, x + 0.5, y, claim.maxZ() + 0.5, dust);
        }
        for (int z = claim.minZ(); z <= claim.maxZ(); z++) {
            spawn(player, world, claim.minX() + 0.5, y, z + 0.5, dust);
            spawn(player, world, claim.maxX() + 0.5, y, z + 0.5, dust);
        }
    }

    private static void spawn(Player player, World world, double x, double y, double z,
                              Particle.DustOptions dust) {
        player.spawnParticle(Particle.DUST, new Location(world, x, y, z), 1, 0, 0, 0, 0, dust);
    }
}
