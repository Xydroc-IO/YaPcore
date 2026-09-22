package com.yapcore.qol;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;

/** PDC tags so timber / excavator listeners recognize YaPItems tool definitions. */
public final class ToolTags {

    public static final NamespacedKey TYPE = key("tool_type");
    public static final NamespacedKey SIZE = key("mine_size");

    private ToolTags() {
    }

    public static void apply(ItemMeta meta, String id) {
        if (meta == null || id == null) {
            return;
        }
        String key = id.toLowerCase(Locale.ROOT);
        if ("timber_axe".equals(key)) {
            meta.getPersistentDataContainer().set(TYPE, PersistentDataType.STRING, QolItems.TIMBER_AXE);
            return;
        }
        int size = excavatorSize(key);
        if (size > 0) {
            meta.getPersistentDataContainer().set(TYPE, PersistentDataType.STRING, QolItems.EXCAVATOR);
            meta.getPersistentDataContainer().set(SIZE, PersistentDataType.INTEGER, size);
        }
    }

    /** @return mine size, or {@code 0} when {@code id} is not an excavator */
    public static int excavatorSize(String id) {
        if (id == null) {
            return 0;
        }
        String key = id.toLowerCase(Locale.ROOT);
        if ("excavator".equals(key) || "excavator_3".equals(key)) {
            return 3;
        }
        if (key.startsWith("excavator_")) {
            try {
                return Integer.parseInt(key.substring("excavator_".length()));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private static NamespacedKey key(String name) {
        NamespacedKey parsed = NamespacedKey.fromString("yap-qol:" + name);
        if (parsed == null) {
            throw new IllegalStateException("yap-qol:" + name);
        }
        return parsed;
    }
}
