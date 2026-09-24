package com.yapcore.yap420;

import com.yapcore.sched.YapSched;
import com.yapcore.yap420.cure.RackDisplayService;
import com.yapcore.yap420.cure.RackRegistry;
import com.yapcore.yap420.cure.RackState;
import com.yapcore.yap420.persist.PlotStore;
import com.yapcore.yap420.persist.RackStore;
import com.yapcore.yap420.plant.PlantDisplayService;
import com.yapcore.yap420.plant.PlotRegistry;
import com.yapcore.yap420.plant.PlotState;
import com.yapcore.yap420.press.PressDisplayService;
import com.yapcore.yap420.press.PressRegistry;
import com.yapcore.yap420.press.PressState;
import com.yapcore.yap420.press.PressStore;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/**
 * Respawn / re-bind displays when chunks load.
 * Delayed a few ticks so Folia finishes registering chunk entities before we look them up.
 */
public final class ChunkRespawnListener implements Listener {

    private static final long ENSURE_DELAY_TICKS = 5L;

    private final JavaPlugin plugin;
    private final PlotRegistry plots;
    private final RackRegistry racks;
    private final PressRegistry presses;
    private final PlantDisplayService plants;
    private final RackDisplayService rackDisplays;
    private final PressDisplayService pressDisplays;
    private final PlotStore plotStore;
    private final RackStore rackStore;
    private final PressStore pressStore;

    public ChunkRespawnListener(
            JavaPlugin plugin,
            PlotRegistry plots,
            RackRegistry racks,
            PressRegistry presses,
            PlantDisplayService plants,
            RackDisplayService rackDisplays,
            PressDisplayService pressDisplays,
            PlotStore plotStore,
            RackStore rackStore,
            PressStore pressStore
    ) {
        this.plugin = plugin;
        this.plots = plots;
        this.racks = racks;
        this.presses = presses;
        this.plants = plants;
        this.rackDisplays = rackDisplays;
        this.pressDisplays = pressDisplays;
        this.plotStore = plotStore;
        this.rackStore = rackStore;
        this.pressStore = pressStore;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        final int cx = chunk.getX();
        final int cz = chunk.getZ();
        final String worldName = event.getWorld().getName();
        YapSched.regionChunkLater(plugin, event.getWorld(), cx, cz, () -> {
            boolean plotDirty = false;
            for (PlotState plot : plots.inChunk(worldName, cx, cz)) {
                PlotState before = plot;
                PlotState updated = plants.ensureDisplay(plot);
                plots.put(updated);
                if (!Objects.equals(before.entityUuid(), updated.entityUuid())) {
                    plotDirty = true;
                }
            }
            if (plotDirty) {
                plotStore.saveAsync();
            }
            boolean rackDirty = false;
            for (RackState rack : racks.inChunk(worldName, cx, cz)) {
                RackState before = rack;
                RackState updated = rackDisplays.ensureDisplay(rack);
                racks.put(updated);
                if (!Objects.equals(before.entityUuid(), updated.entityUuid())) {
                    rackDirty = true;
                }
            }
            if (rackDirty) {
                rackStore.saveAsync();
            }
            boolean pressDirty = false;
            for (PressState press : presses.inChunk(worldName, cx, cz)) {
                PressState before = press;
                PressState updated = pressDisplays.ensureDisplay(press);
                presses.put(updated);
                if (!Objects.equals(before.entityUuid(), updated.entityUuid())) {
                    pressDirty = true;
                }
            }
            if (pressDirty) {
                pressStore.saveAsync();
            }
        }, ENSURE_DELAY_TICKS);
    }
}
