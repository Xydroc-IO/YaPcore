package com.yapcore.holo.impl;

import com.yapcore.sched.YapSched;
import com.yapcore.sched.YapTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;

public final class HologramTracker {

    private final JavaPlugin plugin;
    private final HologramAnimations animations;
    private YapTask task;

    public HologramTracker(JavaPlugin plugin, HologramAnimations animations) {
        this.plugin = plugin;
        this.animations = animations;
    }

    public void start(int periodTicks, Collection<HologramImpl> holograms) {
        stop();
        long period = Math.max(5L, periodTicks);
        task = YapSched.globalTimer(plugin, () -> tick(holograms), period, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick(Collection<HologramImpl> holograms) {
        if (holograms.isEmpty()) {
            return;
        }
        animations.advance();
        for (HologramImpl holo : holograms) {
            HologramFollow.schedule(plugin, holo);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            YapSched.entity(plugin, player, () -> {
                for (HologramImpl holo : holograms) {
                    holo.refresh(player);
                }
            });
        }
    }
}
