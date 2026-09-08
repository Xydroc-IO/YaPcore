package com.yapcore.items;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/** Persistent data keys for YaPItems stacks and furniture entities. */
public final class ItemsKeys {

    private final NamespacedKey itemId;
    private final NamespacedKey itemRev;
    private final NamespacedKey furnitureId;

    public ItemsKeys(JavaPlugin plugin) {
        this.itemId = new NamespacedKey(plugin, "yap_item_id");
        this.itemRev = new NamespacedKey(plugin, "yap_item_rev");
        this.furnitureId = new NamespacedKey(plugin, "yap_furniture_id");
    }

    public NamespacedKey itemId() {
        return itemId;
    }

    public NamespacedKey itemRev() {
        return itemRev;
    }

    public NamespacedKey furnitureId() {
        return furnitureId;
    }
}
