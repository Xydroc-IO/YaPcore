package com.yapcore.yap420.item;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Optional;

/** Soft bridge to YaPItems ItemService + inventory helpers. */
public final class ItemBridge {

    public Optional<ItemStack> create(String itemId, int amount) {
        return com.yapcore.items.api.ItemServices.find().flatMap(svc -> svc.create(itemId, amount));
    }

    public Optional<String> idOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return Optional.empty();
        }
        return com.yapcore.items.api.ItemServices.find().flatMap(svc -> svc.idOf(stack));
    }

    public boolean has(String itemId) {
        return com.yapcore.items.api.ItemServices.find().map(svc -> svc.has(itemId)).orElse(false);
    }

    public Optional<com.yapcore.items.api.ItemService> service() {
        return com.yapcore.items.api.ItemServices.find();
    }

    public int count(Inventory inv, String itemId) {
        if (inv == null || itemId == null) {
            return 0;
        }
        int total = 0;
        for (ItemStack stack : inv.getContents()) {
            if (stack == null) {
                continue;
            }
            Optional<String> sid = idOf(stack);
            if (sid.isPresent() && sid.get().equalsIgnoreCase(itemId)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /** Removes up to {@code amount} matching stacks. Returns how many were removed. */
    public int take(PlayerInventory inv, String itemId, int amount) {
        if (inv == null || itemId == null || amount <= 0) {
            return 0;
        }
        int remaining = amount;
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null) {
                continue;
            }
            Optional<String> sid = idOf(stack);
            if (sid.isEmpty() || !sid.get().equalsIgnoreCase(itemId)) {
                continue;
            }
            int take = Math.min(remaining, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            if (stack.getAmount() <= 0) {
                inv.setItem(i, null);
            }
            remaining -= take;
        }
        return amount - remaining;
    }

    /** Give stacks, dropping leftovers at the player's feet when inventory is full. */
    public boolean giveOrDrop(org.bukkit.entity.Player player, String itemId, int amount) {
        if (player == null || amount <= 0) {
            return false;
        }
        ItemStack stack = create(itemId, amount).orElse(null);
        if (stack == null) {
            return false;
        }
        player.getInventory().addItem(stack).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        return true;
    }
}
