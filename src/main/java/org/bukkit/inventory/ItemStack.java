package org.bukkit.inventory;

import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Objects;

public class ItemStack implements Cloneable {
    private Material type;
    private int amount;
    private ItemMeta meta;

    public ItemStack(Material type) {
        this(type, 1);
    }

    public ItemStack(Material type, int amount) {
        this.type = Objects.requireNonNull(type, "type");
        this.amount = Math.max(0, amount);
    }

    public Material getType() {
        return type;
    }

    public void setType(Material type) {
        this.type = Objects.requireNonNull(type);
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = Math.max(0, amount);
    }

    public boolean hasItemMeta() {
        return meta != null;
    }

    public ItemMeta getItemMeta() {
        return meta == null ? null : meta.clone();
    }

    public boolean setItemMeta(ItemMeta meta) {
        this.meta = meta == null ? null : meta.clone();
        return true;
    }

    /** @deprecated use ItemMeta display name */
    @Deprecated
    public String getDisplayName() {
        return meta != null && meta.hasDisplayName() ? meta.getDisplayName() : null;
    }

    @Deprecated
    public void setDisplayName(String displayName) {
        if (meta == null) {
            meta = new com.yapcore.compat.SimpleItemMeta();
        }
        meta.setDisplayName(displayName);
    }

    @Override
    public ItemStack clone() {
        ItemStack copy = new ItemStack(type, amount);
        if (meta != null) {
            copy.meta = meta.clone();
        }
        return copy;
    }

    @Override
    public String toString() {
        return "ItemStack{" + type + " x" + amount + "}";
    }
}
