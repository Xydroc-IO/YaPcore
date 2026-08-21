package org.bukkit.inventory;

import java.util.HashMap;
import java.util.List;

public interface Inventory {
    int getSize();

    ItemStack getItem(int index);

    void setItem(int index, ItemStack item);

    HashMap<Integer, ItemStack> addItem(ItemStack... items);

    HashMap<Integer, ItemStack> removeItem(ItemStack... items);

    ItemStack[] getContents();

    void setContents(ItemStack[] items);

    void clear();

    String getTitle();

    default InventoryHolder getHolder() {
        return null;
    }

    default List<ItemStack> getStorageContents() {
        return List.of(getContents());
    }
}
