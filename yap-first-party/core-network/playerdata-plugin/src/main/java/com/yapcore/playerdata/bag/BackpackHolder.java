package com.yapcore.playerdata.bag;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/** Custom chest holder so clicks never key off the title string alone. */
public final class BackpackHolder implements InventoryHolder {

    private final UUID owner;
    private final String ownerName;
    private final int page;
    private final int pages;
    private final boolean staffView;
    private Inventory inventory;

    public BackpackHolder(UUID owner, String ownerName, int page, int pages, boolean staffView) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.page = page;
        this.pages = pages;
        this.staffView = staffView;
    }

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public int page() {
        return page;
    }

    public int pages() {
        return pages;
    }

    public boolean staffView() {
        return staffView;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void bind(Inventory inventory) {
        this.inventory = inventory;
    }
}
