package com.yapcore.yapblock.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class IslandSettingsHolder implements InventoryHolder {

    private final long islandId;
    private Inventory inventory;

    public IslandSettingsHolder(long islandId) {
        this.islandId = islandId;
    }

    public long islandId() {
        return islandId;
    }

    void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
