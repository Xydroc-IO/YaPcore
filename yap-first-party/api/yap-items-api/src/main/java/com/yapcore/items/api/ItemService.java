package com.yapcore.items.api;

import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Optional;

/** Soft API for YaPItems — kits and other plugins resolve custom stacks without hard-depending. */
public interface ItemService {

    Optional<ItemStack> create(String itemId);

    Optional<ItemStack> create(String itemId, int amount);

    Optional<String> idOf(ItemStack stack);

    boolean isCustom(ItemStack stack);

    Collection<String> ids();

    boolean has(String itemId);

    /**
     * Current ability cooldown label for {@code itemId} (e.g. {@code 8s}), or empty if the item
     * has no ability / is unknown.
     */
    Optional<String> abilityCooldown(String itemId);

    /**
     * Persist {@code ability.cooldown} for {@code itemId}, reload the registry, and return whether
     * the write succeeded.
     */
    boolean setAbilityCooldown(String itemId, String duration);
}
