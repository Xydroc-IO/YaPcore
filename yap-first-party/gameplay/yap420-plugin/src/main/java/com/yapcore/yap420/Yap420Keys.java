package com.yapcore.yap420;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/** PDC keys for plants, racks, and presses. */
public final class Yap420Keys {

    private final NamespacedKey plotId;
    private final NamespacedKey rackId;
    private final NamespacedKey pressId;
    private final NamespacedKey strain;
    private final NamespacedKey stage;

    public Yap420Keys(JavaPlugin plugin) {
        this.plotId = new NamespacedKey(plugin, "plot_id");
        this.rackId = new NamespacedKey(plugin, "rack_id");
        this.pressId = new NamespacedKey(plugin, "press_id");
        this.strain = new NamespacedKey(plugin, "strain");
        this.stage = new NamespacedKey(plugin, "stage");
    }

    public NamespacedKey plotId() {
        return plotId;
    }

    public NamespacedKey rackId() {
        return rackId;
    }

    public NamespacedKey pressId() {
        return pressId;
    }

    public NamespacedKey strain() {
        return strain;
    }

    public NamespacedKey stage() {
        return stage;
    }
}
