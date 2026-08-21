package com.yapcore.compat;

import com.yapcore.bridge.CompatibilityBridge;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Arrays;
import java.util.HashMap;

/**
 * Inventory whose mutations are staged onto the Compatibility Bridge
 * (safe from plugin async / heavy pools).
 */
public final class BridgedPlayerInventory implements PlayerInventory {

    private final String owner;
    private final String pluginSource;
    private final CompatibilityBridge bridge;
    private final ItemStack[] contents = new ItemStack[41];
    private ItemStack mainHand = new ItemStack(Material.AIR);
    private ItemStack offHand = new ItemStack(Material.AIR);
    private final ItemStack[] armor = new ItemStack[4];

    public BridgedPlayerInventory(String owner, String pluginSource, CompatibilityBridge bridge) {
        this.owner = owner;
        this.pluginSource = pluginSource;
        this.bridge = bridge;
        Arrays.fill(armor, new ItemStack(Material.AIR));
    }

    private void stage(String desc, Runnable action) {
        bridge.submitLegacyMutation(pluginSource, owner + ":" + desc, action);
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
        stage("setItem-" + index, () -> contents[index] = item == null ? null : item.clone());
    }

    @Override
    public HashMap<Integer, ItemStack> addItem(ItemStack... items) {
        HashMap<Integer, ItemStack> leftover = new HashMap<>();
        stage("addItem", () -> {
            for (ItemStack item : items) {
                if (item == null) {
                    continue;
                }
                boolean placed = false;
                for (int i = 0; i < contents.length; i++) {
                    if (contents[i] == null || contents[i].getType().isAir()) {
                        contents[i] = item.clone();
                        placed = true;
                        break;
                    }
                }
                if (!placed) {
                    leftover.put(leftover.size(), item);
                }
            }
        });
        return leftover;
    }

    @Override
    public HashMap<Integer, ItemStack> removeItem(ItemStack... items) {
        HashMap<Integer, ItemStack> leftover = new HashMap<>();
        stage("removeItem", () -> {
            for (ItemStack item : items) {
                if (item == null) {
                    continue;
                }
                for (int i = 0; i < contents.length; i++) {
                    if (contents[i] != null && contents[i].getType() == item.getType()) {
                        contents[i] = null;
                        break;
                    }
                }
            }
        });
        return leftover;
    }

    @Override
    public ItemStack[] getContents() {
        return contents.clone();
    }

    @Override
    public void setContents(ItemStack[] items) {
        stage("setContents", () -> {
            Arrays.fill(contents, null);
            if (items != null) {
                System.arraycopy(items, 0, contents, 0, Math.min(items.length, contents.length));
            }
        });
    }

    @Override
    public void clear() {
        stage("clear", () -> Arrays.fill(contents, null));
    }

    @Override
    public String getTitle() {
        return owner + "'s Inventory";
    }

    @Override
    public ItemStack getItemInMainHand() {
        return mainHand;
    }

    @Override
    public void setItemInMainHand(ItemStack item) {
        stage("setMainHand", () -> mainHand = item == null ? new ItemStack(Material.AIR) : item.clone());
    }

    @Override
    public ItemStack getItemInOffHand() {
        return offHand;
    }

    @Override
    public void setItemInOffHand(ItemStack item) {
        stage("setOffHand", () -> offHand = item == null ? new ItemStack(Material.AIR) : item.clone());
    }

    @Override
    public ItemStack[] getArmorContents() {
        return armor.clone();
    }

    @Override
    public void setArmorContents(ItemStack[] items) {
        stage("setArmor", () -> {
            Arrays.fill(armor, new ItemStack(Material.AIR));
            if (items != null) {
                System.arraycopy(items, 0, armor, 0, Math.min(items.length, armor.length));
            }
        });
    }
}
