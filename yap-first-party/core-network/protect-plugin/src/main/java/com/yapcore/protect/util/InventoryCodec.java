package com.yapcore.protect.util;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Base64;

/**
 * Serializes container contents as {@code slot:base64Bytes|slot:base64Bytes…}.
 * Uses Paper {@link ItemStack#serializeAsBytes()} / {@link ItemStack#deserializeBytes(byte[])}.
 */
public final class InventoryCodec {

    private static final char SLOT_SEP = ';';
    private static final char DATA_SEP = ':';

    private InventoryCodec() {
    }

    public static String encode(Inventory inventory) {
        if (inventory == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(SLOT_SEP);
            }
            sb.append(slot).append(DATA_SEP)
                    .append(Base64.getEncoder().encodeToString(stack.serializeAsBytes()));
        }
        return sb.toString();
    }

    public static void apply(Inventory inventory, String encoded) {
        if (inventory == null) {
            return;
        }
        ItemStack[] contents = new ItemStack[inventory.getSize()];
        if (encoded != null && !encoded.isBlank()) {
            for (String part : encoded.split(String.valueOf(SLOT_SEP))) {
                if (part.isBlank()) {
                    continue;
                }
                int sep = part.indexOf(DATA_SEP);
                if (sep <= 0) {
                    continue;
                }
                try {
                    int slot = Integer.parseInt(part.substring(0, sep));
                    byte[] bytes = Base64.getDecoder().decode(part.substring(sep + 1));
                    if (slot >= 0 && slot < contents.length) {
                        contents[slot] = ItemStack.deserializeBytes(bytes);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        inventory.setContents(contents);
    }

    public static boolean equalsEncoded(String a, String b) {
        if (a == null) {
            a = "";
        }
        if (b == null) {
            b = "";
        }
        return a.equals(b);
    }
}
