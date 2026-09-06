package com.yapcore.core;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Tiny counter surface for plugin sandbox inventory mutations.
 * Replaces the deleted product {@code GameCore} dependency — live ticks are Folia /
 * chassis {@link com.yaplabs.yapengine.core.spatial.ParallelGameCore}.
 */
public interface InventoryMutationCounter {

    long getInventoryMutations();

    AtomicLong inventoryMutationCounter();
}
