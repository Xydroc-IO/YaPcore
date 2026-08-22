package com.yapcore.playerdata.sync;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * Bukkit YAML ItemStack[] ↔ bytes for MEDIUMBLOB columns.
 */
public final class ItemSerializer {

    private ItemSerializer() {
    }

    public static byte[] serialize(ItemStack[] items) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("size", items == null ? 0 : items.length);
        if (items != null) {
            for (int i = 0; i < items.length; i++) {
                if (items[i] != null) {
                    yaml.set("i." + i, items[i]);
                }
            }
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            writer.write(yaml.saveToString());
            writer.flush();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize inventory", e);
        }
    }

    public static ItemStack[] deserialize(byte[] data, int fallbackSize) {
        if (data == null || data.length == 0) {
            return new ItemStack[Math.max(0, fallbackSize)];
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(data), StandardCharsets.UTF_8)) {
            yaml.load(reader);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize inventory", e);
        }
        int size = yaml.getInt("size", fallbackSize);
        ItemStack[] items = new ItemStack[Math.max(size, 0)];
        for (int i = 0; i < items.length; i++) {
            if (yaml.contains("i." + i)) {
                items[i] = yaml.getItemStack("i." + i);
            }
        }
        return items;
    }

    /** Empty blob for first insert. */
    public static byte[] empty(int size) {
        return serialize(new ItemStack[size]);
    }
}
