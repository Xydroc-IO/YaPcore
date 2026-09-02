package com.yapcore.playerdata.bag;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Paper custom GUIs expose the plugin holder via {@code getHolder(false)}. */
final class BackpackInventories {

    private BackpackInventories() {
    }

    static InventoryHolder holder(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        InventoryHolder custom = inventory.getHolder(false);
        return custom != null ? custom : inventory.getHolder();
    }

    static BackpackHolder bag(Inventory inventory) {
        return holder(inventory) instanceof BackpackHolder bag ? bag : null;
    }
}
