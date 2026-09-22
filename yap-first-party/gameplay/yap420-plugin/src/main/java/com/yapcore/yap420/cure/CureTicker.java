package com.yapcore.yap420.cure;

import com.yapcore.sched.YapSched;
import com.yapcore.sched.YapTask;
import com.yapcore.yap420.Yap420Config;
import com.yapcore.yap420.persist.RackStore;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/** Marks rack slots cured after configured dwell time. */
public final class CureTicker {

    private final JavaPlugin plugin;
    private final Yap420Config config;
    private final RackRegistry racks;
    private final RackStore store;
    private YapTask task;

    public CureTicker(JavaPlugin plugin, Yap420Config config, RackRegistry racks, RackStore store) {
        this.plugin = plugin;
        this.config = config;
        this.racks = racks;
        this.store = store;
    }

    public void start() {
        stop();
        task = YapSched.globalTimer(plugin, this::tickAll, config.curePeriodTicks(), config.curePeriodTicks());
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tickAll() {
        long now = System.currentTimeMillis();
        for (RackState rack : racks.all()) {
            World world = Bukkit.getWorld(rack.world());
            if (world == null) {
                continue;
            }
            YapSched.region(plugin, world, rack.x(), rack.z(), () -> advance(rack, now));
        }
    }

    private void advance(RackState snapshot, long now) {
        RackState rack = racks.get(snapshot.world(), snapshot.x(), snapshot.y(), snapshot.z()).orElse(null);
        if (rack == null) {
            return;
        }
        boolean changed = false;
        List<RackState.Slot> next = new ArrayList<>(rack.slots().size());
        for (RackState.Slot slot : rack.slots()) {
            if (!slot.cured() && now - slot.depositedAtMs() >= config.cureMillisPerBud()) {
                next.add(slot.markCured());
                changed = true;
            } else {
                next.add(slot);
            }
        }
        if (changed) {
            racks.put(rack.withSlots(next));
            store.saveAsync();
        }
    }
}
