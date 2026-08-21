package org.bukkit.inventory;

import org.bukkit.entity.Player;

/** Currently open inventory view for a player. */
public interface InventoryView {
    Inventory getTopInventory();

    Inventory getBottomInventory();

    Player getPlayer();

    String getTitle();

    void close();
}
