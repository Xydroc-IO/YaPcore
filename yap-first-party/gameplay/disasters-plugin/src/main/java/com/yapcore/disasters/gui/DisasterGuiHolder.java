package com.yapcore.disasters.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class DisasterGuiHolder implements InventoryHolder {

    private final String worldName;
    private Inventory inventory;

    public DisasterGuiHolder(String worldName) {
        this.worldName = worldName;
    }

    public String worldName() {
        return worldName;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void bind(Inventory inventory) {
        this.inventory = inventory;
    }
}
