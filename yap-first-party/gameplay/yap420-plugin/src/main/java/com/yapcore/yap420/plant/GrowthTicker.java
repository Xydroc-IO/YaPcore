package com.yapcore.yap420.plant;

import com.yapcore.sched.YapSched;
import com.yapcore.sched.YapTask;
import com.yapcore.yap420.Yap420Config;
import com.yapcore.yap420.persist.PlotStore;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

/** Region-safe growth advances. */
public final class GrowthTicker {

    private final JavaPlugin plugin;
    private final Yap420Config config;
    private final PlotRegistry plots;
    private final PlantDisplayService displays;
    private final PlotStore store;
    private YapTask task;

    public GrowthTicker(
            JavaPlugin plugin,
            Yap420Config config,
            PlotRegistry plots,
            PlantDisplayService displays,
            PlotStore store
    ) {
        this.plugin = plugin;
        this.config = config;
        this.plots = plots;
        this.displays = displays;
        this.store = store;
    }

    public void start() {
        stop();
        task = YapSched.globalTimer(plugin, this::tickAll, config.growthPeriodTicks(), config.growthPeriodTicks());
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tickAll() {
        for (PlotState plot : plots.all()) {
            if (PlotStore.isEphemeralWorld(plot.world())) {
                continue;
            }
            World world = Bukkit.getWorld(plot.world());
            if (world == null || !world.isChunkLoaded(plot.chunkX(), plot.chunkZ())) {
                continue;
            }
            // Heal missing visuals for mature plants (growth path skips them otherwise)
            if (plot.stage() >= config.maxStageIndex()) {
                YapSched.region(plugin, world, plot.x(), plot.z(), () -> {
                    PlotState current = plots.get(plot.world(), plot.x(), plot.y(), plot.z()).orElse(null);
                    if (current == null) {
                        return;
                    }
                    PlotState ensured = displays.ensureDisplay(current);
                    if (ensured.entityUuid() == null
                            || !ensured.entityUuid().equals(current.entityUuid())) {
                        plots.put(ensured);
                        store.saveAsync();
                    }
                });
                continue;
            }
            YapSched.region(plugin, world, plot.x(), plot.z(), () -> advanceIfReady(plot));
        }
    }

    private void advanceIfReady(PlotState snapshot) {
        PlotState plot = plots.get(snapshot.world(), snapshot.x(), snapshot.y(), snapshot.z()).orElse(null);
        if (plot == null || plot.stage() >= config.maxStageIndex()) {
            return;
        }
        if (PlotStore.isEphemeralWorld(plot.world())) {
            return;
        }
        World world = Bukkit.getWorld(plot.world());
        if (world == null) {
            return;
        }
        Block plantBlock = world.getBlockAt(plot.x(), plot.y(), plot.z());
        Block soil = plantBlock.getRelative(0, -1, 0);
        boolean wildSoil = config.wild().soils().contains(soil.getType());
        if (!GrowthRules.canAdvance(
                plantBlock,
                config.minLight(),
                config.requireWater() && !wildSoil,
                config.waterRadius(),
                config.growthSoils(),
                config.advanceChance())) {
            return;
        }
        PlotState next = plot.withStage(plot.stage() + 1, plot.entityUuid());
        displays.safeUpdate(next, plots);
        store.saveAsync();
    }
}
