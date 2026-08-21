package org.bukkit.inventory;

public interface PlayerInventory extends Inventory {
    ItemStack getItemInMainHand();

    void setItemInMainHand(ItemStack item);

    ItemStack getItemInOffHand();

    void setItemInOffHand(ItemStack item);

    ItemStack[] getArmorContents();

    void setArmorContents(ItemStack[] items);
}
