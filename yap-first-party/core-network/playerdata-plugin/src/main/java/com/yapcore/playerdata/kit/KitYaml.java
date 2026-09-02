package com.yapcore.playerdata.kit;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Load/save {@code kits.yml} with legacy material rows and full Bukkit ItemStacks. */
public final class KitYaml {

    private KitYaml() {
    }

    public static Map<String, KitDef> load(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, KitDef> out = new LinkedHashMap<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection ks = section.getConfigurationSection(id);
            if (ks == null) {
                continue;
            }
            KitDef def = readKit(id.toLowerCase(Locale.ROOT), ks);
            out.put(def.id(), def);
        }
        return out;
    }

    public static KitDef readKit(String id, ConfigurationSection ks) {
        long delay = ks.getLong("delay-seconds", ks.getLong("delay", 86400));
        int maxUses = ks.getInt("max-uses", ks.getInt("maxuses", 0));
        double cost = ks.getDouble("cost", 0);
        boolean firstJoin = ks.getBoolean("first-join", ks.getBoolean("kit-on-join", false));
        List<String> commands = ks.getStringList("commands");

        List<ItemStack> items = new ArrayList<>();
        ItemStack helmet = readGear(ks, "helmet", "armor.helmet");
        ItemStack chest = readGear(ks, "chestplate", "armor.chestplate");
        ItemStack legs = readGear(ks, "leggings", "armor.leggings");
        ItemStack boots = readGear(ks, "boots", "armor.boots");
        ItemStack offhand = readGear(ks, "offhand", "armor.offhand");

        List<?> raw = ks.getList("items");
        if (raw != null) {
            for (Object o : raw) {
                Parsed parsed = parseItem(o);
                if (parsed == null || parsed.stack() == null) {
                    continue;
                }
                switch (parsed.slot()) {
                    case HELMET -> helmet = parsed.stack();
                    case CHESTPLATE -> chest = parsed.stack();
                    case LEGGINGS -> legs = parsed.stack();
                    case BOOTS -> boots = parsed.stack();
                    case OFFHAND -> offhand = parsed.stack();
                    default -> items.add(parsed.stack());
                }
            }
        }
        return new KitDef(id, delay, maxUses, cost, firstJoin, items, helmet, chest, legs, boots, offhand, commands);
    }

    public static void saveKit(JavaPlugin plugin, KitDef def) throws IOException {
        File file = new File(plugin.getDataFolder(), "kits.yml");
        YamlConfiguration yaml = file.isFile() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        String base = "kits." + def.id();
        yaml.set(base, null);
        yaml.set(base + ".delay-seconds", def.delaySeconds());
        yaml.set(base + ".max-uses", def.maxUses());
        yaml.set(base + ".cost", def.cost());
        yaml.set(base + ".first-join", def.firstJoin());
        yaml.set(base + ".commands", def.commands());
        yaml.set(base + ".items", def.items());
        if (def.helmet() != null) {
            yaml.set(base + ".armor.helmet", def.helmet());
        }
        if (def.chestplate() != null) {
            yaml.set(base + ".armor.chestplate", def.chestplate());
        }
        if (def.leggings() != null) {
            yaml.set(base + ".armor.leggings", def.leggings());
        }
        if (def.boots() != null) {
            yaml.set(base + ".armor.boots", def.boots());
        }
        if (def.offhand() != null) {
            yaml.set(base + ".offhand", def.offhand());
        }
        yaml.save(file);
    }

    public static void deleteKit(JavaPlugin plugin, String id) throws IOException {
        File file = new File(plugin.getDataFolder(), "kits.yml");
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.set("kits." + id.toLowerCase(Locale.ROOT), null);
        yaml.save(file);
    }

    private static ItemStack readGear(ConfigurationSection ks, String key, String nested) {
        if (ks.contains(key)) {
            ItemStack stack = ks.getItemStack(key);
            if (stack != null) {
                return stack;
            }
            Material mat = Material.matchMaterial(ks.getString(key, ""));
            if (mat != null && mat.isItem()) {
                return new ItemStack(mat);
            }
        }
        if (ks.contains(nested)) {
            ItemStack stack = ks.getItemStack(nested);
            if (stack != null) {
                return stack;
            }
            String path = nested.contains(".") ? nested.substring(nested.indexOf('.') + 1) : nested;
            ConfigurationSection armor = ks.getConfigurationSection("armor");
            if (armor != null) {
                Material mat = Material.matchMaterial(armor.getString(path, ""));
                if (mat != null && mat.isItem()) {
                    return new ItemStack(mat);
                }
            }
        }
        return null;
    }

    public record Parsed(ItemStack stack, Slot slot) {
    }

    public enum Slot {
        INVENTORY, HELMET, CHESTPLATE, LEGGINGS, BOOTS, OFFHAND
    }

    @SuppressWarnings("unchecked")
    public static Parsed parseItem(Object o) {
        if (o instanceof ItemStack stack) {
            return new Parsed(stack.clone(), Slot.INVENTORY);
        }
        if (!(o instanceof Map<?, ?> map)) {
            return null;
        }
        Map<String, Object> raw = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            raw.put(String.valueOf(e.getKey()), e.getValue());
        }
        Slot slot = slotOf(String.valueOf(raw.getOrDefault("slot", "")));
        if (raw.containsKey("==") || raw.containsKey("type")) {
            try {
                ItemStack stack = ItemStack.deserialize(raw);
                if (stack != null && !stack.getType().isAir()) {
                    return new Parsed(stack, slot);
                }
            } catch (Exception ignored) {
                // fall through to material form
            }
        }
        Object matObj = raw.get("material");
        if (matObj == null) {
            matObj = raw.get("type");
        }
        if (matObj == null) {
            return null;
        }
        Material material = Material.matchMaterial(String.valueOf(matObj));
        if (material == null || !material.isItem()) {
            return null;
        }
        int amount = raw.get("amount") instanceof Number n ? Math.max(1, n.intValue()) : 1;
        ItemStack stack = new ItemStack(material, amount);
        applyMeta(stack, raw);
        return new Parsed(stack, slot);
    }

    private static void applyMeta(ItemStack stack, Map<String, Object> raw) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        if (raw.get("name") instanceof String name && !name.isBlank()) {
            meta.displayName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(name));
        }
        if (raw.get("lore") instanceof List<?> lore) {
            List<net.kyori.adventure.text.Component> lines = new ArrayList<>();
            for (Object line : lore) {
                lines.add(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(String.valueOf(line)));
            }
            meta.lore(lines);
        }
        if (raw.get("custom-model-data") instanceof Number n) {
            meta.setCustomModelData(n.intValue());
        }
        stack.setItemMeta(meta);
        Object ench = raw.get("enchantments");
        if (ench instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                Enchantment enchantment = enchantment(String.valueOf(e.getKey()));
                int level = e.getValue() instanceof Number n ? n.intValue() : 1;
                if (enchantment != null) {
                    stack.addUnsafeEnchantment(enchantment, Math.max(1, level));
                }
            }
        }
    }

    private static Enchantment enchantment(String name) {
        String key = name.toLowerCase(Locale.ROOT).replace(' ', '_');
        if (key.startsWith("minecraft:")) {
            key = key.substring("minecraft:".length());
        }
        try {
            Enchantment byKey = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
            if (byKey != null) {
                return byKey;
            }
        } catch (Exception ignored) {
        }
        return Enchantment.getByName(key.toUpperCase(Locale.ROOT));
    }

    private static Slot slotOf(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "helmet", "head" -> Slot.HELMET;
            case "chest", "chestplate" -> Slot.CHESTPLATE;
            case "legs", "leggings" -> Slot.LEGGINGS;
            case "boots", "feet" -> Slot.BOOTS;
            case "offhand", "off-hand", "shield" -> Slot.OFFHAND;
            default -> Slot.INVENTORY;
        };
    }
}
