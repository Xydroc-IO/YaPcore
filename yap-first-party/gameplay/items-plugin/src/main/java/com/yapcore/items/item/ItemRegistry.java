package com.yapcore.items.item;

import com.yapcore.items.ItemsConfig;
import com.yapcore.items.ability.AbilityDefinition;
import com.yapcore.items.ability.AbilityType;
import com.yapcore.mmo.GearBonus;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

/** Loads items/*.yml (+ items/custom/) into an id → definition map. */
public final class ItemRegistry {

    private final JavaPlugin plugin;
    private final ItemsConfig config;
    private final Map<String, ItemDefinition> byId = new LinkedHashMap<>();
    private int loadRevision;

    public ItemRegistry(JavaPlugin plugin, ItemsConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void reload() {
        byId.clear();
        loadRevision++;
        File itemsDir = new File(plugin.getDataFolder(), "items");
        if (!itemsDir.isDirectory()) {
            plugin.saveResource("items/examples.yml", false);
        }
        File customDir = new File(itemsDir, "custom");
        if (!customDir.isDirectory() && !customDir.mkdirs()) {
            plugin.getLogger().warning("Could not create items/custom/");
        }
        loadDir(itemsDir, false);
        loadDir(customDir, true);
        plugin.getLogger().info("Loaded " + byId.size() + " custom items (rev " + loadRevision + ")");
    }

    private void loadDir(File dir, boolean customOnly) {
        if (!dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles((d, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                continue;
            }
            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                for (String key : yaml.getKeys(false)) {
                    ConfigurationSection section = yaml.getConfigurationSection(key);
                    if (section == null) {
                        continue;
                    }
                    String id = key.toLowerCase(Locale.ROOT);
                    try {
                        ItemDefinition def = parse(id, section);
                        byId.put(id, def);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "Bad item '" + id + "' in " + file.getName(), e);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed reading " + file.getName(), e);
            }
        }
    }

    public ItemDefinition parse(String id, ConfigurationSection ks) {
        Material base = Material.matchMaterial(ks.getString("base", "PAPER"));
        if (base == null || !base.isItem()) {
            throw new IllegalArgumentException("invalid base material for " + id);
        }
        String name = ks.getString("name", id);
        List<String> lore = ks.getStringList("lore");
        int cmd = ks.getInt("custom-model-data", 0);
        if (cmd != 0 && !config.inCmdRange(cmd)) {
            plugin.getLogger().warning("Item " + id + " CMD " + cmd + " outside " + config.cmdMin() + "-" + config.cmdMax());
        }
        boolean unbreakable = ks.getBoolean("unbreakable", false);
        boolean glow = ks.getBoolean("glow", false);
        List<ItemFlag> flags = new ArrayList<>();
        for (String f : ks.getStringList("hide-flags")) {
            try {
                flags.add(ItemFlag.valueOf(f.trim().toUpperCase(Locale.ROOT).replace('-', '_')));
            } catch (IllegalArgumentException ignored) {
            }
        }
        Map<Enchantment, Integer> enchants = new LinkedHashMap<>();
        ConfigurationSection enchSec = ks.getConfigurationSection("enchants");
        if (enchSec != null) {
            for (String ek : enchSec.getKeys(false)) {
                Enchantment ench = enchantment(ek);
                if (ench != null) {
                    enchants.put(ench, Math.max(1, enchSec.getInt(ek)));
                }
            }
        }
        Map<String, Double> attributes = new LinkedHashMap<>();
        ConfigurationSection attrSec = ks.getConfigurationSection("attributes");
        if (attrSec != null) {
            for (String ak : attrSec.getKeys(false)) {
                attributes.put(ak.toLowerCase(Locale.ROOT), attrSec.getDouble(ak));
            }
        }
        String permission = ks.getString("permission", null);
        GearBonus gear = GearBonus.ZERO;
        ConfigurationSection gearSec = ks.getConfigurationSection("gear");
        if (gearSec != null) {
            gear = new GearBonus(
                    gearSec.getInt("attack", 0),
                    gearSec.getInt("strength", 0),
                    gearSec.getInt("defence", gearSec.getInt("defense", 0)),
                    gearSec.getInt("prayer", 0),
                    gearSec.getInt("ranged", 0),
                    gearSec.getInt("magic", 0));
        }
        List<AbilityDefinition> abilities = parseAbilities(ks);
        boolean consume = ks.getBoolean("consume", false);
        ItemDefinition.FurnitureDef furniture = null;
        ConfigurationSection furSec = ks.getConfigurationSection("furniture");
        if (furSec != null) {
            furniture = new ItemDefinition.FurnitureDef(
                    furSec.getBoolean("enabled", false),
                    (float) furSec.getDouble("scale", 1.0),
                    furSec.getBoolean("solid", false),
                    furSec.getString("place-sound", "BLOCK_STONE_PLACE"),
                    furSec.getBoolean("break-drops", true));
        }
        ItemDefinition.RecipeDef recipe = null;
        ConfigurationSection recSec = ks.getConfigurationSection("recipe");
        if (recSec != null) {
            String type = recSec.getString("type", "shaped");
            List<String> shape = recSec.getStringList("shape");
            Map<Character, Material> ingredients = new LinkedHashMap<>();
            ConfigurationSection ing = recSec.getConfigurationSection("ingredients");
            if (ing != null) {
                for (String ik : ing.getKeys(false)) {
                    if (ik.isEmpty()) {
                        continue;
                    }
                    Material m = Material.matchMaterial(ing.getString(ik, "AIR"));
                    if (m != null && m != Material.AIR) {
                        ingredients.put(ik.charAt(0), m);
                    }
                }
            }
            List<Material> shapeless = new ArrayList<>();
            for (String sm : recSec.getStringList("ingredients-list")) {
                Material m = Material.matchMaterial(sm);
                if (m != null && m.isItem()) {
                    shapeless.add(m);
                }
            }
            recipe = new ItemDefinition.RecipeDef(type, shape, ingredients, shapeless);
        }
        return new ItemDefinition(
                id,
                base,
                name,
                List.copyOf(lore),
                cmd,
                unbreakable,
                glow,
                List.copyOf(flags),
                Map.copyOf(enchants),
                Map.copyOf(attributes),
                permission,
                gear,
                abilities,
                consume,
                furniture,
                recipe,
                loadRevision);
    }

    private List<AbilityDefinition> parseAbilities(ConfigurationSection ks) {
        List<AbilityDefinition> out = new ArrayList<>();
        List<Map<?, ?>> list = ks.getMapList("abilities");
        for (Map<?, ?> raw : list) {
            AbilityDefinition parsed = parseAbilityMap(raw);
            if (parsed != null) {
                out.add(parsed);
            }
        }
        if (out.isEmpty()) {
            ConfigurationSection abSec = ks.getConfigurationSection("ability");
            if (abSec != null) {
                AbilityDefinition one = parseAbilitySection(abSec);
                if (one != null) {
                    out.add(one);
                }
            }
        }
        return List.copyOf(out);
    }

    private AbilityDefinition parseAbilitySection(ConfigurationSection abSec) {
        if (abSec == null || abSec.getString("type") == null) {
            return null;
        }
        try {
            AbilityDefinition.Trigger trigger = AbilityDefinition.Trigger.parse(abSec.getString("trigger"));
            AbilityType type = AbilityType.parse(abSec.getString("type"));
            long cd = AbilityDefinition.parseCooldown(abSec.getString("cooldown", "0s"));
            String abPerm = abSec.getString("permission", null);
            Map<String, Object> params = new LinkedHashMap<>();
            ConfigurationSection pSec = abSec.getConfigurationSection("params");
            if (pSec != null) {
                for (String pk : pSec.getKeys(false)) {
                    params.put(pk, pSec.get(pk));
                }
            }
            return new AbilityDefinition(trigger, type, cd, abPerm, Collections.unmodifiableMap(params));
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Bad ability in item config: " + e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private AbilityDefinition parseAbilityMap(Map<?, ?> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        YamlConfiguration tmp = new YamlConfiguration();
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            String key = String.valueOf(e.getKey());
            Object val = e.getValue();
            if (val instanceof Map<?, ?> nested) {
                ConfigurationSection sec = tmp.createSection(key);
                for (Map.Entry<?, ?> ne : nested.entrySet()) {
                    sec.set(String.valueOf(ne.getKey()), ne.getValue());
                }
            } else {
                tmp.set(key, val);
            }
        }
        return parseAbilitySection(tmp);
    }

    public Optional<ItemDefinition> get(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(id.toLowerCase(Locale.ROOT)));
    }

    public Map<String, ItemDefinition> all() {
        return Collections.unmodifiableMap(byId);
    }

    public int size() {
        return byId.size();
    }

    public int loadRevision() {
        return loadRevision;
    }

    public int nextFreeCmd() {
        for (int c = config.cmdMin(); c <= config.cmdMax(); c++) {
            final int probe = c;
            boolean used = byId.values().stream().anyMatch(d -> d.customModelData() == probe);
            if (!used) {
                return c;
            }
        }
        return config.cmdMin();
    }

    public void put(ItemDefinition def) {
        byId.put(def.id(), def);
    }

    public void remove(String id) {
        byId.remove(id.toLowerCase(Locale.ROOT));
    }

    /** Ensure jar defaults exist under data folder (first run). */
    public void ensureDefaults() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create data folder");
        }
        File examples = new File(plugin.getDataFolder(), "items/examples.yml");
        if (!examples.isFile()) {
            plugin.saveResource("items/examples.yml", false);
        }
        try (InputStream in = plugin.getResource("config.yml")) {
            if (in != null && !new File(plugin.getDataFolder(), "config.yml").isFile()) {
                plugin.saveDefaultConfig();
            }
        } catch (Exception ignored) {
            plugin.saveDefaultConfig();
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

    public static void copyResourceIfMissing(JavaPlugin plugin, String path) throws IOException {
        File out = new File(plugin.getDataFolder(), path);
        if (out.isFile()) {
            return;
        }
        out.getParentFile().mkdirs();
        try (InputStream in = plugin.getResource(path)) {
            if (in == null) {
                return;
            }
            Files.copy(in, out.toPath());
        }
    }
}
