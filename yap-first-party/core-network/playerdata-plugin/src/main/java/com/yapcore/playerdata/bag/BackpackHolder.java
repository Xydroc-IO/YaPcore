package com.yapcore.playerdata.bag;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Custom chest holder so clicks never key off the title string alone.
 *
 * @param itemNav when true, bottom row is page chrome (vanilla / Bedrock).
 *                when false, GUI is exactly {@link BackpackService#STORAGE_SLOTS}
 *                and page changes come from the Fabric yap-bag tabs.
 */
public final class BackpackHolder implements InventoryHolder {

    private final UUID owner;
    private final String ownerName;
    private final int page;
    private final int pages;
    private final boolean staffView;
    private final boolean itemNav;
    private Inventory inventory;

    public BackpackHolder(UUID owner, String ownerName, int page, int pages,
                          boolean staffView, boolean itemNav) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.page = page;
        this.pages = pages;
        this.staffView = staffView;
        this.itemNav = itemNav;
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

    /** Bottom-row arrow/paper chrome (no Fabric yap-bag tabs). */
    public boolean itemNav() {
        return itemNav;
    }

    public int guiSize() {
        return itemNav ? BackpackService.GUI_SIZE : BackpackService.STORAGE_SLOTS;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void bind(Inventory inventory) {
        this.inventory = inventory;
    }
}
