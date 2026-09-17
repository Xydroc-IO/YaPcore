package com.yapcore.playerdata.npc;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/** Compact enchant meta for npc_offers.meta_json: {@code enchants=minecraft:sharpness:5,minecraft:unbreaking:3}. */
public final class OfferItemMeta {

    private OfferItemMeta() {
    }

    public static String encode(Map<Enchantment, Integer> enchants) {
        if (enchants == null || enchants.isEmpty()) {
            return null;
        }
        StringJoiner joiner = new StringJoiner(",");
        for (var e : enchants.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue() <= 0) {
                continue;
            }
            NamespacedKey key = e.getKey().getKey();
            joiner.add(key.asString() + ":" + e.getValue());
        }
        String body = joiner.toString();
        return body.isEmpty() ? null : "enchants=" + body;
    }

    public static Map<Enchantment, Integer> decode(String metaJson) {
        if (metaJson == null || metaJson.isBlank()) {
            return Map.of();
        }
        String raw = metaJson.trim();
        if (raw.startsWith("enchants=")) {
            raw = raw.substring("enchants=".length());
        } else if (raw.startsWith("{")) {
            // tolerate older accidental JSON drafts
            return decodeLooseJson(raw);
        }
        Map<Enchantment, Integer> out = new LinkedHashMap<>();
        for (String part : raw.split(",")) {
            String p = part.trim();
            if (p.isEmpty()) {
                continue;
            }
            int colon = p.lastIndexOf(':');
            if (colon <= 0) {
                continue;
            }
            String keyStr = p.substring(0, colon).trim();
            int level;
            try {
                level = Integer.parseInt(p.substring(colon + 1).trim());
            } catch (NumberFormatException e) {
                continue;
            }
            Enchantment ench = resolve(keyStr);
            if (ench != null && level > 0) {
                out.put(ench, level);
            }
        }
        return Collections.unmodifiableMap(out);
    }

    public static ItemStack buildStack(Material material, int amount, String metaJson) {
        ItemStack stack = new ItemStack(material, Math.max(1, amount));
        Map<Enchantment, Integer> enchants = decode(metaJson);
        if (enchants.isEmpty()) {
            return stack;
        }
        if (material == Material.ENCHANTED_BOOK) {
            stack.editMeta(EnchantmentStorageMeta.class, meta -> {
                for (var e : enchants.entrySet()) {
                    meta.addStoredEnchant(e.getKey(), e.getValue(), true);
                }
            });
        } else {
            stack.editMeta(ItemMeta.class, meta -> {
                for (var e : enchants.entrySet()) {
                    meta.addEnchant(e.getKey(), e.getValue(), true);
                }
            });
        }
        return stack;
    }

    public static boolean hasMeta(String metaJson) {
        return metaJson != null && !metaJson.isBlank();
    }

    private static Enchantment resolve(String keyStr) {
        NamespacedKey key = NamespacedKey.fromString(keyStr.toLowerCase(Locale.ROOT));
        if (key == null) {
            return null;
        }
        return Registry.ENCHANTMENT.get(key);
    }

    private static Map<Enchantment, Integer> decodeLooseJson(String raw) {
        // minimal: "minecraft:foo":N pairs
        Map<Enchantment, Integer> out = new LinkedHashMap<>();
        int i = 0;
        while (i < raw.length()) {
            int q1 = raw.indexOf('"', i);
            if (q1 < 0) {
                break;
            }
            int q2 = raw.indexOf('"', q1 + 1);
            if (q2 < 0) {
                break;
            }
            String keyStr = raw.substring(q1 + 1, q2);
            int colon = raw.indexOf(':', q2 + 1);
            if (colon < 0) {
                break;
            }
            int numStart = colon + 1;
            while (numStart < raw.length() && !Character.isDigit(raw.charAt(numStart))) {
                numStart++;
            }
            int numEnd = numStart;
            while (numEnd < raw.length() && Character.isDigit(raw.charAt(numEnd))) {
                numEnd++;
            }
            if (numStart < numEnd) {
                try {
                    int level = Integer.parseInt(raw.substring(numStart, numEnd));
                    Enchantment ench = resolve(keyStr);
                    if (ench != null) {
                        out.put(ench, level);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            i = numEnd;
        }
        return Collections.unmodifiableMap(out);
    }
}
