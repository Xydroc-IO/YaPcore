package com.yapcore.items.item;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;

/** Writes wizard-created items under items/custom/. */
public final class ItemWriter {

    private final JavaPlugin plugin;

    public ItemWriter(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public File writeCustom(ItemCreateRequest req) throws IOException {
        File dir = new File(plugin.getDataFolder(), "items/custom");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Cannot create items/custom");
        }
        String id = req.id().toLowerCase(Locale.ROOT);
        File file = new File(dir, id + ".yml");
        YamlConfiguration yaml = new YamlConfiguration();
        String base = id;
        yaml.set(base + ".base", req.base().name());
        yaml.set(base + ".name", req.name());
        if (req.lore() != null && !req.lore().isEmpty()) {
            yaml.set(base + ".lore", req.lore());
        }
        yaml.set(base + ".custom-model-data", req.cmd());
        if (req.glow()) {
            yaml.set(base + ".glow", true);
        }
        if (req.unbreakable()) {
            yaml.set(base + ".unbreakable", true);
        }
        if (req.abilities() != null && !req.abilities().isEmpty()) {
            java.util.List<java.util.Map<String, Object>> abilityMaps = new java.util.ArrayList<>();
            for (ItemCreateRequest.AbilityWrite ab : req.abilities()) {
                if (ab == null || ab.type() == null || ab.type().isBlank()) {
                    continue;
                }
                java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("trigger", ab.trigger() == null || ab.trigger().isBlank() ? "RIGHT_CLICK" : ab.trigger());
                m.put("type", ab.type());
                m.put("cooldown", ab.cooldown() == null ? "5s" : ab.cooldown());
                if (ab.params() != null && !ab.params().isEmpty()) {
                    m.put("params", new java.util.LinkedHashMap<>(ab.params()));
                }
                abilityMaps.add(m);
            }
            if (!abilityMaps.isEmpty()) {
                yaml.set(base + ".abilities", abilityMaps);
            }
        }
        if (req.furniture()) {
            yaml.set(base + ".furniture.enabled", true);
            yaml.set(base + ".furniture.scale", 1.0);
            yaml.set(base + ".furniture.break-drops", true);
        }
        if (req.gearAttack() != 0 || req.gearStrength() != 0) {
            yaml.set(base + ".gear.attack", req.gearAttack());
            yaml.set(base + ".gear.strength", req.gearStrength());
        }
        yaml.save(file);
        return file;
    }

    public boolean isCustom(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        File file = new File(plugin.getDataFolder(), "items/custom/" + id.toLowerCase(Locale.ROOT) + ".yml");
        return file.isFile();
    }

    public boolean deleteCustom(String id) {
        File file = new File(plugin.getDataFolder(), "items/custom/" + id.toLowerCase(Locale.ROOT) + ".yml");
        return file.isFile() && file.delete();
    }

    /**
     * Sets {@code ability.cooldown} on the YAML definition for {@code id}.
     * Searches {@code items/*.yml} and {@code items/custom/*.yml}.
     *
     * @return the file written, or empty if the item definition was not found on disk
     */
    public java.util.Optional<File> setAbilityCooldown(String id, String cooldown) throws IOException {
        String itemId = id.toLowerCase(Locale.ROOT);
        String normalized = normalizeCooldown(cooldown);
        File found = findDefinitionFile(itemId);
        if (found == null) {
            return java.util.Optional.empty();
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(found);
        if (!yaml.isConfigurationSection(itemId)) {
            return java.util.Optional.empty();
        }
        boolean wrote = false;
        java.util.List<java.util.Map<?, ?>> list = yaml.getMapList(itemId + ".abilities");
        if (list != null && !list.isEmpty()) {
            java.util.List<java.util.Map<String, Object>> updated = new java.util.ArrayList<>();
            for (java.util.Map<?, ?> raw : list) {
                java.util.Map<String, Object> copy = new java.util.LinkedHashMap<>();
                for (java.util.Map.Entry<?, ?> e : raw.entrySet()) {
                    copy.put(String.valueOf(e.getKey()), e.getValue());
                }
                copy.put("cooldown", normalized);
                updated.add(copy);
            }
            yaml.set(itemId + ".abilities", updated);
            wrote = true;
        }
        if (yaml.isConfigurationSection(itemId + ".ability") || !wrote) {
            if (!yaml.isConfigurationSection(itemId + ".ability")) {
                yaml.set(itemId + ".ability.trigger", "RIGHT_CLICK");
                yaml.set(itemId + ".ability.type", "message");
                yaml.set(itemId + ".ability.params.text", "&7Ability");
            }
            yaml.set(itemId + ".ability.cooldown", normalized);
            wrote = true;
        }
        if (!wrote) {
            return java.util.Optional.empty();
        }
        yaml.save(found);
        return java.util.Optional.of(found);
    }

    public static String normalizeCooldown(String raw) {
        if (raw == null || raw.isBlank()) {
            return "0s";
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.matches("\\d+(\\.\\d+)?")) {
            return s + "s";
        }
        return s;
    }

    private File findDefinitionFile(String itemId) {
        File itemsDir = new File(plugin.getDataFolder(), "items");
        File hit = scanDir(itemsDir, itemId);
        if (hit != null) {
            return hit;
        }
        return scanDir(new File(itemsDir, "custom"), itemId);
    }

    private static File scanDir(File dir, String itemId) {
        if (dir == null || !dir.isDirectory()) {
            return null;
        }
        File[] files = dir.listFiles((d, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null) {
            return null;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                continue;
            }
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            if (yaml.isConfigurationSection(itemId)) {
                return file;
            }
        }
        return null;
    }

    public static Material templateBase(String template) {
        if (template == null) {
            return Material.PAPER;
        }
        return switch (template.toLowerCase(Locale.ROOT)) {
            case "sword" -> Material.NETHERITE_SWORD;
            case "axe" -> Material.NETHERITE_AXE;
            case "spear" -> Material.NETHERITE_SPEAR;
            case "mace" -> Material.MACE;
            case "bow" -> Material.BOW;
            case "crossbow" -> Material.CROSSBOW;
            case "trident" -> Material.TRIDENT;
            case "shield" -> Material.SHIELD;
            case "wand", "staff" -> Material.BLAZE_ROD;
            case "pickaxe", "tool" -> Material.NETHERITE_PICKAXE;
            case "shovel" -> Material.NETHERITE_SHOVEL;
            case "hoe" -> Material.NETHERITE_HOE;
            case "shears" -> Material.SHEARS;
            case "fishing_rod" -> Material.FISHING_ROD;
            case "amethyst", "gem", "charm" -> Material.AMETHYST_SHARD;
            case "emerald" -> Material.EMERALD;
            case "diamond" -> Material.DIAMOND;
            case "nether_star" -> Material.NETHER_STAR;
            case "echo_shard" -> Material.ECHO_SHARD;
            case "totem" -> Material.TOTEM_OF_UNDYING;
            case "golden_apple" -> Material.GOLDEN_APPLE;
            case "heart_of_the_sea" -> Material.HEART_OF_THE_SEA;
            case "prismarine" -> Material.PRISMARINE_SHARD;
            case "prop", "furniture", "prop_paper" -> Material.PAPER;
            case "prop_oak" -> Material.OAK_PLANKS;
            case "prop_stone" -> Material.STONE;
            case "prop_chest" -> Material.CHEST;
            case "prop_ender_chest" -> Material.ENDER_CHEST;
            case "prop_lantern" -> Material.LANTERN;
            case "prop_soul_lantern" -> Material.SOUL_LANTERN;
            case "prop_beacon" -> Material.BEACON;
            case "prop_pot" -> Material.FLOWER_POT;
            case "prop_bell" -> Material.BELL;
            case "prop_armor_stand" -> Material.ARMOR_STAND;
            case "key" -> Material.TRIPWIRE_HOOK;
            default -> {
                Material m = Material.matchMaterial(template);
                yield m != null && m.isItem() ? m : Material.PAPER;
            }
        };
    }

    public static boolean templateIsFurniture(String template) {
        if (template == null) {
            return false;
        }
        String t = template.toLowerCase(Locale.ROOT);
        return t.equals("prop") || t.equals("furniture") || t.startsWith("prop_");
    }

    public static int templateCmd(String template, int fallback) {
        if (template == null) {
            return fallback;
        }
        // Must match resourcepacks/yap-items template overrides (not example item CMDs).
        // New bases use a free CMD from the registry (vanilla look until pack adds models).
        return switch (template.toLowerCase(Locale.ROOT)) {
            case "sword" -> 12010;
            case "tool", "pickaxe" -> 12011;
            case "gem", "charm", "amethyst" -> 12012;
            case "prop", "furniture", "prop_paper" -> 12013;
            case "key" -> 12005;
            default -> fallback;
        };
    }
}
