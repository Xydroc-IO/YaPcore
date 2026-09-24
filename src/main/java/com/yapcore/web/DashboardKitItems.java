package com.yapcore.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Encode/decode kit items for the dashboard kit builder. */
public final class DashboardKitItems {

    private DashboardKitItems() {
    }

    /** Encode items for a flat POST body (one line per item). */
    public static String encodeItems(List<Map<String, Object>> items) {
        StringBuilder sb = new StringBuilder();
        if (items == null) {
            return "";
        }
        for (Map<String, Object> item : items) {
            String line = encodeItem(item);
            if (line == null || line.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(line);
        }
        return sb.toString();
    }

    public static List<Map<String, Object>> decodeItems(String raw) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String line : raw.split("\n")) {
            Map<String, Object> item = decodeItem(line);
            if (item != null) {
                out.add(item);
            }
        }
        return out;
    }

    public static String encodeItem(Map<String, Object> item) {
        if (isYapItem(item)) {
            String id = yapItemIdOf(item);
            if (id.isBlank()) {
                return "";
            }
            return String.join("|",
                    "YAP:" + id,
                    str(item.get("amount"), "1"),
                    str(item.get("slot"), "inventory"),
                    "",
                    "",
                    "");
        }
        return String.join("|",
                str(item.get("material"), "STONE").toUpperCase(Locale.ROOT).replace(' ', '_'),
                str(item.get("amount"), "1"),
                str(item.get("slot"), "inventory"),
                escapeField(str(item.get("name"), "")),
                escapeField(str(item.get("lore"), "")),
                escapeField(str(item.get("enchantments"), "")));
    }

    public static Map<String, Object> decodeItem(String line) {
        if (line == null || line.isBlank() || line.trim().startsWith("#")) {
            return null;
        }
        String trimmed = line.trim();
        Map<String, Object> item = new LinkedHashMap<>();
        if (!trimmed.contains("|") && trimmed.contains(":")) {
            String[] parts = trimmed.split(":", 2);
            String left = parts[0].trim();
            if ("yap".equalsIgnoreCase(left) || "yap-item".equalsIgnoreCase(left)
                    || "yap_item".equalsIgnoreCase(left)) {
                String id = parts.length > 1 ? parts[1].trim().toLowerCase(Locale.ROOT) : "";
                if (id.isEmpty()) {
                    return null;
                }
                item.put("kind", "yap-item");
                item.put("yapItemId", id);
                item.put("material", "");
                item.put("amount", 1);
                item.put("slot", "inventory");
                item.put("name", "");
                item.put("lore", "");
                item.put("enchantments", "");
                return item;
            }
            item.put("kind", "material");
            item.put("material", left.toUpperCase(Locale.ROOT).replace(' ', '_'));
            item.put("amount", parseAmount(parts.length > 1 ? parts[1] : "1"));
            item.put("slot", "inventory");
            item.put("name", "");
            item.put("lore", "");
            item.put("enchantments", "");
            return item;
        }
        if (!trimmed.contains("|")) {
            if (trimmed.toUpperCase(Locale.ROOT).startsWith("YAP:")) {
                String id = trimmed.substring(4).trim().toLowerCase(Locale.ROOT);
                if (id.isEmpty()) {
                    return null;
                }
                item.put("kind", "yap-item");
                item.put("yapItemId", id);
                item.put("material", "");
                item.put("amount", 1);
                item.put("slot", "inventory");
                item.put("name", "");
                item.put("lore", "");
                item.put("enchantments", "");
                return item;
            }
            item.put("kind", "material");
            item.put("material", trimmed.toUpperCase(Locale.ROOT).replace(' ', '_'));
            item.put("amount", 1);
            item.put("slot", "inventory");
            item.put("name", "");
            item.put("lore", "");
            item.put("enchantments", "");
            return item;
        }
        String[] parts = trimmed.split("\\|", -1);
        String material = unescapeField(parts[0]).trim();
        if (material.isEmpty()) {
            return null;
        }
        if (material.toUpperCase(Locale.ROOT).startsWith("YAP:")) {
            String id = material.substring(4).trim().toLowerCase(Locale.ROOT);
            if (id.isEmpty()) {
                return null;
            }
            item.put("kind", "yap-item");
            item.put("yapItemId", id);
            item.put("material", "");
            item.put("amount", parseAmount(parts.length > 1 ? parts[1] : "1"));
            item.put("slot", normalizeSlot(parts.length > 2 ? parts[2] : "inventory"));
            item.put("name", "");
            item.put("lore", "");
            item.put("enchantments", "");
            return item;
        }
        item.put("kind", "material");
        item.put("material", material.toUpperCase(Locale.ROOT).replace(' ', '_'));
        item.put("amount", parseAmount(parts.length > 1 ? parts[1] : "1"));
        item.put("slot", normalizeSlot(parts.length > 2 ? parts[2] : "inventory"));
        item.put("name", parts.length > 3 ? unescapeField(parts[3]) : "");
        item.put("lore", parts.length > 4 ? unescapeField(parts[4]) : "");
        item.put("enchantments", parts.length > 5 ? unescapeField(parts[5]) : "");
        return item;
    }

    public static String normalizeId(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> toKit(String id, Map<String, Object> raw) {
        Map<String, Object> kit = new LinkedHashMap<>();
        kit.put("id", normalizeId(id));
        kit.put("delaySeconds", longVal(first(raw, "delay-seconds", "delay"), 86400));
        kit.put("maxUses", intVal(first(raw, "max-uses", "maxuses"), 0));
        kit.put("cost", doubleVal(raw.get("cost"), 0));
        kit.put("extraCost", doubleVal(first(raw, "extra-cost", "paid-cost"), 0));
        kit.put("firstJoin", boolVal(first(raw, "first-join", "kit-on-join"), false));
        kit.put("commands", stringList(raw.get("commands")));
        List<Map<String, Object>> items = new ArrayList<>();
        if (raw.get("items") instanceof List<?> list) {
            for (Object row : list) {
                Map<String, Object> item = itemFromYaml(row);
                if (item != null) {
                    items.add(item);
                }
            }
        }
        addGear(items, raw, "helmet", "helmet");
        addGear(items, raw, "chestplate", "chestplate");
        addGear(items, raw, "leggings", "leggings");
        addGear(items, raw, "boots", "boots");
        addGear(items, raw, "offhand", "offhand");
        Object armor = raw.get("armor");
        if (armor instanceof Map<?, ?> am) {
            Map<String, Object> armorMap = (Map<String, Object>) am;
            addGear(items, armorMap, "helmet", "helmet");
            addGear(items, armorMap, "chestplate", "chestplate");
            addGear(items, armorMap, "leggings", "leggings");
            addGear(items, armorMap, "boots", "boots");
            addGear(items, armorMap, "offhand", "offhand");
        }
        kit.put("items", items);
        kit.put("itemCount", items.size());
        return kit;
    }

    static Map<String, Object> fromKit(Map<String, Object> kit) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("delay-seconds", longVal(kit.get("delaySeconds"), 86400));
        out.put("max-uses", intVal(kit.get("maxUses"), 0));
        out.put("cost", doubleVal(kit.get("cost"), 0));
        out.put("extra-cost", doubleVal(kit.get("extraCost"), 0));
        out.put("first-join", boolVal(kit.get("firstJoin"), false));
        out.put("commands", stringList(kit.get("commands")));
        List<Map<String, Object>> rows = new ArrayList<>();
        Object itemsObj = kit.get("items");
        if (itemsObj instanceof List<?> list) {
            for (Object row : list) {
                if (row instanceof Map<?, ?> map) {
                    Map<String, Object> yamlItem = itemToYaml(castMap(map));
                    if (yamlItem != null) {
                        rows.add(yamlItem);
                    }
                }
            }
        } else if (itemsObj instanceof String encoded) {
            for (Map<String, Object> item : decodeItems(encoded)) {
                Map<String, Object> yamlItem = itemToYaml(item);
                if (yamlItem != null) {
                    rows.add(yamlItem);
                }
            }
        }
        out.put("items", rows);
        return out;
    }

    private static void addGear(List<Map<String, Object>> items, Map<String, Object> raw, String key, String slot) {
        if (!raw.containsKey(key)) {
            return;
        }
        Map<String, Object> item = itemFromYaml(raw.get(key));
        if (item == null) {
            return;
        }
        item.put("slot", slot);
        items.add(item);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> itemFromYaml(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof String s) {
            return decodeItem(s);
        }
        if (!(raw instanceof Map<?, ?> map)) {
            return null;
        }
        Map<String, Object> src = (Map<String, Object>) map;
        String yapId = str(first(src, "yap-item", "yap_item"), "");
        if (!yapId.isBlank()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("kind", "yap-item");
            item.put("yapItemId", yapId.toLowerCase(Locale.ROOT));
            item.put("material", "");
            item.put("amount", intVal(src.get("amount"), 1));
            item.put("slot", normalizeSlot(str(src.get("slot"), "inventory")));
            item.put("name", "");
            item.put("lore", "");
            item.put("enchantments", "");
            return item;
        }
        String material = str(first(src, "material", "type"), "");
        if (material.isBlank()) {
            return null;
        }
        if ("org.bukkit.inventory.ItemStack".equals(material)) {
            material = str(src.get("type"), "");
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("kind", "material");
        item.put("material", material.toUpperCase(Locale.ROOT).replace(' ', '_'));
        item.put("amount", intVal(src.get("amount"), 1));
        item.put("slot", normalizeSlot(str(src.get("slot"), "inventory")));
        item.put("name", str(src.get("name"), ""));
        item.put("lore", joinLore(src.get("lore")));
        item.put("enchantments", joinEnchants(src.get("enchantments")));
        if (src.containsKey("==")) {
            item.put("lossy", true);
        }
        return item;
    }

    private static Map<String, Object> itemToYaml(Map<String, Object> item) {
        if (isYapItem(item)) {
            String id = yapItemIdOf(item);
            if (id.isBlank()) {
                return null;
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("yap-item", id);
            out.put("amount", Math.max(1, intVal(item.get("amount"), 1)));
            String slot = normalizeSlot(str(item.get("slot"), "inventory"));
            if (!"inventory".equals(slot)) {
                out.put("slot", slot);
            }
            return out;
        }
        String material = str(item.get("material"), "").toUpperCase(Locale.ROOT).replace(' ', '_');
        if (material.isBlank()) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("material", material);
        int amount = intVal(item.get("amount"), 1);
        if (amount > 1) {
            out.put("amount", amount);
        } else {
            out.put("amount", 1);
        }
        String slot = normalizeSlot(str(item.get("slot"), "inventory"));
        if (!"inventory".equals(slot)) {
            out.put("slot", slot);
        }
        String name = str(item.get("name"), "");
        if (!name.isBlank()) {
            out.put("name", name);
        }
        List<String> lore = splitLore(str(item.get("lore"), ""));
        if (!lore.isEmpty()) {
            out.put("lore", lore);
        }
        Map<String, Integer> ench = splitEnchants(str(item.get("enchantments"), ""));
        if (!ench.isEmpty()) {
            out.put("enchantments", ench);
        }
        return out;
    }

    private static boolean isYapItem(Map<String, Object> item) {
        if (item == null) {
            return false;
        }
        if ("yap-item".equalsIgnoreCase(str(item.get("kind"), ""))) {
            return true;
        }
        return !yapItemIdOf(item).isBlank();
    }

    private static String yapItemIdOf(Map<String, Object> item) {
        String id = str(item.get("yapItemId"), "");
        if (!id.isBlank()) {
            return id.toLowerCase(Locale.ROOT);
        }
        String mat = str(item.get("material"), "");
        if (mat.toUpperCase(Locale.ROOT).startsWith("YAP:")) {
            return mat.substring(4).trim().toLowerCase(Locale.ROOT);
        }
        return "";
    }

    private static String joinLore(Object raw) {
        return DashboardKitItemFields.joinLore(raw);
    }

    private static List<String> splitLore(String raw) {
        return DashboardKitItemFields.splitLore(raw);
    }

    private static String joinEnchants(Object raw) {
        return DashboardKitItemFields.joinEnchants(raw);
    }

    private static Map<String, Integer> splitEnchants(String raw) {
        return DashboardKitItemFields.splitEnchants(raw);
    }

    private static String normalizeSlot(String raw) {
        return DashboardKitItemFields.normalizeSlot(raw);
    }

    private static String escapeField(String raw) {
        return DashboardKitItemFields.escapeField(raw);
    }

    private static String unescapeField(String raw) {
        return DashboardKitItemFields.unescapeField(raw);
    }

    private static List<String> stringList(Object val) {
        return DashboardKitItemFields.stringList(val);
    }

    private static Object first(Map<String, Object> map, String a, String b) {
        return DashboardKitItemFields.first(map, a, b);
    }

    private static String str(Object val, String fallback) {
        return DashboardKitItemFields.str(val, fallback);
    }

    private static int parseAmount(String raw) {
        return DashboardKitItemFields.parseAmount(raw);
    }

    private static int intVal(Object val, int fallback) {
        return DashboardKitItemFields.intVal(val, fallback);
    }

    private static long longVal(Object val, long fallback) {
        return DashboardKitItemFields.longVal(val, fallback);
    }

    private static double doubleVal(Object val, double fallback) {
        return DashboardKitItemFields.doubleVal(val, fallback);
    }

    private static boolean boolVal(Object val, boolean fallback) {
        return DashboardKitItemFields.boolVal(val, fallback);
    }

    private static Map<String, Object> castMap(Map<?, ?> map) {
        return DashboardKitItemFields.castMap(map);
    }
}
