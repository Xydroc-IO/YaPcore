package com.yapcore.visuals.rainbow;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Detects YaPItems {@code rainbow: true} stacks via synced Bukkit PDC. */
public final class RainbowItemNbt {

    private static final String BUKKIT = "PublicBukkitValues";
    private static final String KEY = "yapitems:yap_rainbow";

    private RainbowItemNbt() {
    }

    public static boolean isRainbow(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null || custom.isEmpty()) {
            return false;
        }
        CompoundTag root = custom.copyTag();
        CompoundTag pdc = root.getCompoundOrEmpty(BUKKIT);
        if (pdc.isEmpty()) {
            return false;
        }
        if (truthy(pdc, KEY)) {
            return true;
        }
        // Tolerate alternate plugin id / key spellings from older jars
        for (String k : pdc.keySet()) {
            if (k != null && k.endsWith(":yap_rainbow") && truthy(pdc, k)) {
                return true;
            }
        }
        return false;
    }

    private static boolean truthy(CompoundTag pdc, String key) {
        if (!pdc.contains(key)) {
            return false;
        }
        Tag tag = pdc.get(key);
        if (tag == null) {
            return false;
        }
        byte id = tag.getId();
        if (id == Tag.TAG_BYTE || id == Tag.TAG_SHORT || id == Tag.TAG_INT || id == Tag.TAG_LONG) {
            return pdc.getLongOr(key, 0L) != 0L;
        }
        if (id == Tag.TAG_STRING) {
            String s = pdc.getStringOr(key, "").trim();
            return !s.isEmpty() && !"0".equals(s) && !"false".equalsIgnoreCase(s);
        }
        return id == Tag.TAG_BYTE_ARRAY || id == Tag.TAG_INT_ARRAY;
    }
}
