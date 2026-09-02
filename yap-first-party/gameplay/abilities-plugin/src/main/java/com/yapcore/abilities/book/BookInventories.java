package com.yapcore.abilities.book;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Paper custom GUIs expose the plugin holder via {@code getHolder(false)}. */
final class BookInventories {

    private BookInventories() {
    }

    static InventoryHolder holder(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        InventoryHolder custom = inventory.getHolder(false);
        return custom != null ? custom : inventory.getHolder();
    }

    static AbilityBookHolder bookHolder(Inventory inventory) {
        return holder(inventory) instanceof AbilityBookHolder book ? book : null;
    }
}
