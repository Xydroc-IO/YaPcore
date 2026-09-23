package com.yapcore.yap420.plant;

import com.yapcore.sched.YapSched;
import com.yapcore.yap420.Yap420Config;
import com.yapcore.yap420.Yap420Plugin;
import com.yapcore.yap420.persist.PlotStore;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Places sparse wild YaP420 plants on newly generated chunks (tree/flower style).
 * Existing explored chunks are untouched.
 */
public final class WildSpawnListener implements Listener {

    private final Yap420Plugin plugin;
    private final PlotRegistry plots;
    private final PlantDisplayService displays;
    private final PlotStore store;

    public WildSpawnListener(
            Yap420Plugin plugin,
            PlotRegistry plots,
            PlantDisplayService displays,
            PlotStore store
    ) {
        this.plugin = plugin;
        this.plots = plots;
        this.displays = displays;
        this.store = store;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk()) {
            return;
        }
        Yap420Config.Wild wild = plugin.yapConfig().wild();
        if (!wild.enabled()) {
            return;
        }
        World world = event.getWorld();
        if (!worldAllowed(world, wild)) {
            return;
        }
        Chunk chunk = event.getChunk();
        YapSched.regionChunk(plugin, world, chunk.getX(), chunk.getZ(), () -> tryPopulate(chunk, wild));
    }

    private void tryPopulate(Chunk chunk, Yap420Config.Wild wild) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        if (rng.nextDouble() >= wild.chancePerChunk()) {
            return;
        }
        World world = chunk.getWorld();
        int placed = 0;
        int attempts = Math.max(1, wild.attemptsPerChunk());
        int max = Math.max(1, wild.maxPerChunk());
        for (int i = 0; i < attempts && placed < max; i++) {
            int lx = rng.nextInt(16);
            int lz = rng.nextInt(16);
            int wx = (chunk.getX() << 4) + lx;
            int wz = (chunk.getZ() << 4) + lz;
            Block soil = surfaceSoil(world, wx, wz, wild.soils());
            if (soil == null) {
                continue;
            }
            if (!biomeAllowed(soil, wild.biomes())) {
                continue;
            }
            Block plantAt = soil.getRelative(BlockFace.UP);
            if (!plantAt.getType().isAir()) {
                continue;
            }
            if (plantAt.getLightFromSky() < wild.minLight()
                    && plantAt.getLightFromBlocks() < wild.minLight()) {
                continue;
            }
            if (plots.contains(world.getName(), plantAt.getX(), plantAt.getY(), plantAt.getZ())) {
                continue;
            }
            StrainId strain = pickStrain(rng, wild.strains());
            int stageMax = Math.max(0, Math.min(wild.initialStageMax(), plugin.yapConfig().maxStageIndex()));
            int stageMin = Math.max(0, Math.min(wild.initialStageMin(), stageMax));
            int stage = stageMin >= stageMax ? stageMin : rng.nextInt(stageMin, stageMax + 1);
            PlotState plot = new PlotState(
                    world.getName(),
                    plantAt.getX(),
                    plantAt.getY(),
                    plantAt.getZ(),
                    strain,
                    stage,
                    System.currentTimeMillis(),
                    null);
            PlotState spawned = displays.spawn(plot);
            plots.put(spawned);
            placed++;
        }
        if (placed > 0) {
            store.saveAsync();
        }
    }

    private static Block surfaceSoil(World world, int x, int z, Set<Material> soils) {
        int y = world.getHighestBlockYAt(x, z);
        Block top = world.getBlockAt(x, y, z);
        // highest block is often leaves/grass; walk down a few for soil
        for (int dy = 0; dy <= 4; dy++) {
            Block cand = top.getRelative(0, -dy, 0);
            if (soils.contains(cand.getType()) && cand.getRelative(BlockFace.UP).getType().isAir()) {
                return cand;
            }
        }
        if (soils.contains(top.getType())) {
            return top;
        }
        return null;
    }

    private static boolean worldAllowed(World world, Yap420Config.Wild wild) {
        World.Environment env = world.getEnvironment();
        if (env == World.Environment.NETHER || env == World.Environment.THE_END) {
            return false;
        }
        String name = world.getName().toLowerCase(Locale.ROOT);
        // Instant dungeon / ephemeral worlds — never plant here (chunk floods → OOM)
        if (name.startsWith("yd_") || name.startsWith("yap_tmp_") || name.startsWith("tmp_")) {
            return false;
        }
        List<String> names = wild.worlds();
        if (names.isEmpty()) {
            return true;
        }
        for (String allowed : names) {
            if (name.equals(allowed.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean biomeAllowed(Block soil, Set<String> biomes) {
        if (biomes.isEmpty()) {
            return true;
        }
        Biome biome = soil.getBiome();
        String key = biome.getKey().getKey().toLowerCase(Locale.ROOT);
        return biomes.contains(key);
    }

    private static StrainId pickStrain(ThreadLocalRandom rng, List<StrainId> strains) {
        if (strains.isEmpty()) {
            return rng.nextBoolean() ? StrainId.SATIVA : StrainId.INDICA;
        }
        return strains.get(rng.nextInt(strains.size()));
    }
}
