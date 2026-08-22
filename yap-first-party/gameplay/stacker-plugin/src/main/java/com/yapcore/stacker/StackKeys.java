package com.yapcore.stacker;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

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

    public StackKeys(JavaPlugin plugin) {
        this.stackSize = new NamespacedKey(plugin, "stack_size");
        this.stacked = new NamespacedKey(plugin, "stacked");
        this.itemStackSize = new NamespacedKey(plugin, "item_stack_size");
        this.spawnerStack = new NamespacedKey(plugin, "spawner_stack");
        this.toolType = new NamespacedKey(plugin, "tool_type");
    }
}
