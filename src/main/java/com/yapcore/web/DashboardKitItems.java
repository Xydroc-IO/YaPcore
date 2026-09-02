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
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(encodeItem(item));
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
            item.put("material", parts[0].trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
            item.put("amount", parseAmount(parts.length > 1 ? parts[1] : "1"));
            item.put("slot", "inventory");
            item.put("name", "");
            item.put("lore", "");
            item.put("enchantments", "");
            return item;
        }
        if (!trimmed.contains("|")) {
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
        String material = str(first(src, "material", "type"), "");
        if (material.isBlank()) {
            return null;
        }
        if ("org.bukkit.inventory.ItemStack".equals(material)) {
            material = str(src.get("type"), "");
        }
        Map<String, Object> item = new LinkedHashMap<>();
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

    private static String joinLore(Object raw) {
        if (raw instanceof List<?> list) {
            List<String> lines = new ArrayList<>();
            for (Object line : list) {
                if (line != null && !String.valueOf(line).isBlank()) {
                    lines.add(String.valueOf(line));
                }
            }
            return String.join(";", lines);
        }
        return raw == null ? "" : String.valueOf(raw);
    }

    private static List<String> splitLore(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String part : raw.split(";")) {
            if (!part.isBlank()) {
                out.add(part.trim());
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static String joinEnchants(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            List<String> parts = new ArrayList<>();
            for (var e : map.entrySet()) {
                parts.add(String.valueOf(e.getKey()).toLowerCase(Locale.ROOT) + ":" + intVal(e.getValue(), 1));
            }
            return String.join(",", parts);
        }
        return raw == null ? "" : String.valueOf(raw);
    }

    private static Map<String, Integer> splitEnchants(String raw) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String part : raw.split(",")) {
            String t = part.trim();
            if (t.isEmpty()) {
                continue;
            }
            String[] kv = t.split(":", 2);
            String key = kv[0].trim().toLowerCase(Locale.ROOT).replace(' ', '_');
            if (key.startsWith("minecraft:")) {
                key = key.substring("minecraft:".length());
            }
            if (!key.isEmpty()) {
                out.put(key, kv.length > 1 ? parseAmount(kv[1]) : 1);
            }
        }
        return out;
    }

    private static String normalizeSlot(String raw) {
        return switch (raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT)) {
            case "helmet", "head" -> "helmet";
            case "chest", "chestplate" -> "chestplate";
            case "legs", "leggings" -> "leggings";
            case "boots", "feet" -> "boots";
            case "offhand", "off-hand", "shield" -> "offhand";
            default -> "inventory";
        };
    }

    private static String escapeField(String raw) {
        return raw.replace("\\", "\\\\").replace("|", "\\|").replace("\n", " ");
    }

    private static String unescapeField(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < raw.length()) {
                sb.append(raw.charAt(++i));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static List<String> stringList(Object val) {
        List<String> out = new ArrayList<>();
        if (val instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    out.add(String.valueOf(item));
                }
            }
        } else if (val instanceof String s && !s.isBlank()) {
            for (String part : s.split("\n")) {
                if (!part.isBlank()) {
                    out.add(part.trim());
                }
            }
        }
        return out;
    }

    private static Object first(Map<String, Object> map, String a, String b) {
        if (map.containsKey(a)) {
            return map.get(a);
        }
        return map.get(b);
    }

    private static String str(Object val, String fallback) {
        if (val == null) {
            return fallback;
        }
        String s = String.valueOf(val).trim();
        return s.isEmpty() ? fallback : s;
    }

    private static int parseAmount(String raw) {
        try {
            return Math.max(1, Integer.parseInt(raw.trim()));
        } catch (Exception e) {
            return 1;
        }
    }

    private static int intVal(Object val, int fallback) {
        if (val instanceof Number n) {
            return n.intValue();
        }
        if (val != null) {
            try {
                return Integer.parseInt(String.valueOf(val).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static long longVal(Object val, long fallback) {
        if (val instanceof Number n) {
            return n.longValue();
        }
        if (val != null) {
            try {
                return Long.parseLong(String.valueOf(val).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static double doubleVal(Object val, double fallback) {
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        if (val != null) {
            try {
                return Double.parseDouble(String.valueOf(val).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static boolean boolVal(Object val, boolean fallback) {
        if (val instanceof Boolean b) {
            return b;
        }
        if (val != null) {
            return Boolean.parseBoolean(String.valueOf(val));
        }
        return fallback;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }
}
