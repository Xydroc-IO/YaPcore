package com.yapcore.qol;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/** PDC keys for YaP-QoL tools. */
public final class QolKeys {

    public final NamespacedKey toolType;
    public final NamespacedKey mineSize;

    public QolKeys(JavaPlugin plugin) {
        this.toolType = new NamespacedKey(plugin, "tool_type");
        this.mineSize = new NamespacedKey(plugin, "mine_size");
    }
}
