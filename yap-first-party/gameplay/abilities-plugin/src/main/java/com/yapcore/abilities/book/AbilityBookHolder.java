package com.yapcore.abilities.book;

import com.yapcore.abilities.AbilityCategory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class AbilityBookHolder implements InventoryHolder {

    public static final int[] ABILITY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    public static final int[] BAR_SLOTS = {37, 38, 39, 40, 41, 42};

    public static final int SLOT_ALL = 1;
    public static final int SLOT_MAGIC = 2;
    public static final int SLOT_RANGED = 3;
    public static final int SLOT_MELEE = 4;
    public static final int SLOT_PRAYER = 5;
    public static final int SLOT_UTILITY = 6;
    public static final int SLOT_HELP = 0;
    public static final int SLOT_CLOSE = 8;
    public static final int SLOT_HOTBAR_LABEL = 36;
    public static final int SLOT_PREV = 45;
    public static final int SLOT_CLEAR = 46;
    public static final int SLOT_NEXT = 53;

    private final UUID viewer;
    private AbilityCategory categoryFilter;
    private int page;
    private String pendingAbilityId;
    private Inventory inventory;

    public AbilityBookHolder(UUID viewer, AbilityCategory categoryFilter, int page) {
        this.viewer = viewer;
        this.categoryFilter = categoryFilter;
        this.page = page;
    }

    public UUID viewer() {
        return viewer;
    }

    public AbilityCategory categoryFilter() {
        return categoryFilter;
    }

    public void setCategoryFilter(AbilityCategory categoryFilter) {
        this.categoryFilter = categoryFilter;
    }

    public int page() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public String pendingAbilityId() {
        return pendingAbilityId;
    }

    public void setPendingAbilityId(String pendingAbilityId) {
        this.pendingAbilityId = pendingAbilityId;
    }

    public void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public static boolean isAbilitySlot(int slot) {
        for (int s : ABILITY_SLOTS) {
            if (s == slot) {
                return true;
            }
        }
        return false;
    }

    public static boolean isBarSlot(int slot) {
        for (int s : BAR_SLOTS) {
            if (s == slot) {
                return true;
            }
        }
        return false;
    }

    public static int abilitySlotIndex(int slot) {
        for (int i = 0; i < ABILITY_SLOTS.length; i++) {
            if (ABILITY_SLOTS[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    public static int barSlotIndex(int slot) {
        for (int i = 0; i < BAR_SLOTS.length; i++) {
            if (BAR_SLOTS[i] == slot) {
                return i;
            }
        }
        return -1;
    }
}
