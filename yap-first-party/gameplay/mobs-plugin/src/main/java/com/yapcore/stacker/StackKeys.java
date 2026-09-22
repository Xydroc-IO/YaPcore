package com.yapcore.stacker;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

/** PDC keys for mobs, items, spawners, and tools. */
public final class StackKeys {

    public static final PersistentDataType<Integer, Integer> INT = PersistentDataType.INTEGER;
    public static final PersistentDataType<Byte, Byte> BYTE = PersistentDataType.BYTE;
    public static final PersistentDataType<String, String> STRING = PersistentDataType.STRING;

    public final NamespacedKey stackSize;
    public final NamespacedKey stacked;
    public final NamespacedKey itemStackSize;
    public final NamespacedKey spawnerStack;
    public final NamespacedKey toolType;

    public StackKeys() {
        // Keep the YaPStacker namespace so existing stacked entities stay readable.
        this.stackSize = key("stack_size");
        this.stacked = key("stacked");
        this.itemStackSize = key("item_stack_size");
        this.spawnerStack = key("spawner_stack");
        this.toolType = key("tool_type");
    }

    private static NamespacedKey key(String name) {
        NamespacedKey parsed = NamespacedKey.fromString("yapstacker:" + name);
        if (parsed == null) {
            throw new IllegalStateException("yapstacker:" + name);
        }
        return parsed;
    }
}
