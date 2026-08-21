package com.yapcore.compat;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;

/** Custom chest-style inventory for plugin GUIs. */
public final class BridgedInventory implements Inventory {

    private final ItemStack[] contents;
    private final String title;
    private final InventoryHolder holder;

    public BridgedInventory(InventoryHolder holder, int size, String title) {
        int slots = Math.max(9, Math.min(54, (size / 9) * 9));
        if (size > 0 && size % 9 != 0) {
            slots = Math.min(54, ((size + 8) / 9) * 9);
        }
        this.contents = new ItemStack[slots];
        this.title = title != null ? title : "Chest";
        this.holder = holder;
    }

    @Override
    public int getSize() {
        return contents.length;
    }

    @Override
    public ItemStack getItem(int index) {
        return contents[index];
    }

    @Override
    public void setItem(int index, ItemStack item) {
        contents[index] = item;
    }

    @Override
    public HashMap<Integer, ItemStack> addItem(ItemStack... items) {
        HashMap<Integer, ItemStack> leftover = new HashMap<>();
        int idx = 0;
        for (ItemStack item : items) {
            boolean placed = false;
            for (int i = 0; i < contents.length; i++) {
                if (contents[i] == null) {
                    contents[i] = item;
                    placed = true;
                    break;
                }
            }
            if (!placed) {
                leftover.put(idx, item);
            }
            idx++;
        }
        return leftover;
    }

    @Override
    public HashMap<Integer, ItemStack> removeItem(ItemStack... items) {
        return new HashMap<>();
    }

    @Override
    public ItemStack[] getContents() {
        return contents.clone();
    }

    @Override
    public void setContents(ItemStack[] items) {
        System.arraycopy(items, 0, contents, 0, Math.min(items.length, contents.length));
    }

    @Override
    public void clear() {
        for (int i = 0; i < contents.length; i++) {
            contents[i] = null;
        }
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public InventoryHolder getHolder() {
        return holder;
    }
}
