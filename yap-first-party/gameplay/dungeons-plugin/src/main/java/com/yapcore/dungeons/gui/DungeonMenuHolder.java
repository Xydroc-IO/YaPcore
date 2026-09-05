package com.yapcore.dungeons.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class DungeonMenuHolder implements InventoryHolder {

    private final int page; // 0 = core 1-45, 1 = core 46-50 + nav, 2 = prestige
    private Inventory inventory;

    public DungeonMenuHolder(int page) {
        this.page = page;
    }

    public int page() {
        return page;
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
