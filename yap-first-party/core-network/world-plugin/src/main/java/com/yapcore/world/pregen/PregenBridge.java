package com.yapcore.world.pregen;

import com.yapcore.pregen.PregenPlugin;
import com.yapcore.pregen.shape.ChunkPos;
import com.yapcore.pregen.shape.ChunkShape;
import com.yapcore.pregen.shape.RectShape;
import com.yapcore.pregen.shape.SpiralShape;
import com.yapcore.world.CuboidSelection;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/** Delegates chunk pregen to {@code YaPPregen} when present. */
public final class PregenBridge {

    private PregenBridge() {
    }

    public static boolean available() {
        return Bukkit.getPluginManager().getPlugin("YaPPregen") instanceof PregenPlugin;
    }

    public static String startSelection(World world, CuboidSelection selection) {
        PregenPlugin pregen = plugin();
        if (pregen == null) {
            return "YaPPregen not loaded";
        }
        ChunkShape shape = new RectShape(selection.minX(), selection.minZ(), selection.maxX(), selection.maxZ());
        return pregen.service().startJob(world, shape);
    }

    public static String startRadius(World world, int blockX, int blockZ, int radiusBlocks) {
        PregenPlugin pregen = plugin();
        if (pregen == null) {
            return "YaPPregen not loaded";
        }
        ChunkPos center = ChunkPos.fromBlock(blockX, blockZ);
        int radiusChunks = Math.max(1, (radiusBlocks + 15) / 16);
        ChunkShape shape = new SpiralShape(center.x(), center.z(), radiusChunks);
        return pregen.service().startJob(world, shape);
    }

    public static String pause(String worldOrAll) {
        PregenPlugin pregen = plugin();
        return pregen == null ? "YaPPregen not loaded" : pregen.service().pause(worldOrAll);
    }

    public static String resume(String worldOrAll) {
        PregenPlugin pregen = plugin();
        return pregen == null ? "YaPPregen not loaded" : pregen.service().resume(worldOrAll);
    }

    public static String cancel(String worldOrAll) {
        PregenPlugin pregen = plugin();
        return pregen == null ? "YaPPregen not loaded" : pregen.service().cancel(worldOrAll);
    }

    public static String status(String worldOrAll) {
        PregenPlugin pregen = plugin();
        return pregen == null ? "YaPPregen not loaded" : pregen.service().status(worldOrAll);
    }

    private static PregenPlugin plugin() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("YaPPregen");
        return plugin instanceof PregenPlugin p ? p : null;
    }
}
