package com.yapcore.yap420;

import com.yapcore.sched.YapSched;
import com.yapcore.yap420.cure.RackDisplayService;
import com.yapcore.yap420.cure.RackRegistry;
import com.yapcore.yap420.cure.RackState;
import com.yapcore.yap420.plant.PlantDisplayService;
import com.yapcore.yap420.plant.PlotRegistry;
import com.yapcore.yap420.plant.PlotState;
import com.yapcore.yap420.press.PressDisplayService;
import com.yapcore.yap420.press.PressRegistry;
import com.yapcore.yap420.press.PressState;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Respawn displays when chunks load. */
public final class ChunkRespawnListener implements Listener {

    private final JavaPlugin plugin;
    private final PlotRegistry plots;
    private final RackRegistry racks;
    private final PressRegistry presses;
    private final PlantDisplayService plants;
    private final RackDisplayService rackDisplays;
    private final PressDisplayService pressDisplays;

    public ChunkRespawnListener(
            JavaPlugin plugin,
            PlotRegistry plots,
            RackRegistry racks,
            PressRegistry presses,
            PlantDisplayService plants,
            RackDisplayService rackDisplays,
            PressDisplayService pressDisplays
    ) {
        this.plugin = plugin;
        this.plots = plots;
        this.racks = racks;
        this.presses = presses;
        this.plants = plants;
        this.rackDisplays = rackDisplays;
        this.pressDisplays = pressDisplays;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        YapSched.regionChunk(plugin, event.getWorld(), chunk.getX(), chunk.getZ(), () -> {
            for (PlotState plot : plots.inChunk(event.getWorld().getName(), chunk.getX(), chunk.getZ())) {
                plots.put(plants.ensureDisplay(plot));
            }
            for (RackState rack : racks.inChunk(event.getWorld().getName(), chunk.getX(), chunk.getZ())) {
                racks.put(rackDisplays.ensureDisplay(rack));
            }
            for (PressState press : presses.inChunk(event.getWorld().getName(), chunk.getX(), chunk.getZ())) {
                presses.put(pressDisplays.ensureDisplay(press));
            }
        });
    }
}
