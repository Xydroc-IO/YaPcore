package com.yapcore.crafting.recipe;

import com.yapcore.mmo.RecipeKind;
import com.yapcore.mmo.SkillId;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class RecipePackLoader {

    private static final Logger LOG = Logger.getLogger("YaPCrafting");

    private final Path recipesDir;
    private Map<String, RecipeDefinition> recipes = Map.of();

    public RecipePackLoader(Path recipesDir) {
        this.recipesDir = recipesDir;
    }

    public void reload() {
        Map<String, RecipeDefinition> loaded = new LinkedHashMap<>();
        if (!Files.isDirectory(recipesDir)) {
            recipes = Map.copyOf(loaded);
            return;
        }
        try (var stream = Files.list(recipesDir)) {
            stream.filter(p -> {
                String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                return name.endsWith(".yml") || name.endsWith(".yaml");
            }).forEach(path -> loadFile(path, loaded));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to list recipes in " + recipesDir, e);
        }
        recipes = Map.copyOf(loaded);
    }

    public Map<String, RecipeDefinition> recipes() {
        return recipes;
    }

    public RecipeDefinition get(String id) {
        return recipes.get(id);
    }

    private void loadFile(Path path, Map<String, RecipeDefinition> loaded) {
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
            ConfigurationSection root = yaml.getConfigurationSection("recipes");
            if (root == null) {
                return;
            }
            for (String recipeId : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(recipeId);
                if (section == null) {
                    continue;
                }
                RecipeDefinition def = parse(recipeId, section);
                if (def != null) {
                    loaded.put(def.id(), def);
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load recipes from " + path, e);
        }
    }

    static RecipeDefinition parse(String fallbackId, ConfigurationSection section) {
        String id = section.getString("id", fallbackId);
        RecipeKind kind = parseKind(section.getString("type", "CRAFTING"));
        SkillId skill = SkillId.of(section.getString("skill", defaultSkill(kind)));

        List<RecipeInput> inputs = parseInputs(section.getMapList("inputs"));
        if (inputs.isEmpty()) {
            LOG.warning("Recipe " + id + " has no inputs — skipped");
            return null;
        }

        ConfigurationSection outputSection = section.getConfigurationSection("output");
        if (outputSection == null) {
            LOG.warning("Recipe " + id + " missing output — skipped");
            return null;
        }
        RecipeOutput output = parseOutput(outputSection);
        if (output == null) {
            LOG.warning("Recipe " + id + " invalid output — skipped");
            return null;
        }

        StationType station = parseStation(section.getString("station"), kind, inputs);
        int level = section.getInt("level", 1);
        double xp = section.getDouble("xp", 0);
        int burnLevel = section.getInt("burn-level", 0);
        double burnChance = section.getDouble("burn-chance", 0);
        Material burnOutput = parseMaterial(section.getString("burn-output"), Material.CHARCOAL);
        String displayName = section.getString("name", output.displayName() != null
                ? output.displayName()
                : prettify(id));

        return new RecipeDefinition(
                id,
                kind,
                skill,
                station,
                level,
                inputs,
                output,
                xp,
                burnLevel,
                burnChance,
                burnOutput,
                displayName);
    }

    private static RecipeKind parseKind(String raw) {
        if (raw == null) {
            return RecipeKind.CRAFTING;
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "SMITHING" -> RecipeKind.SMITHING;
            case "COOKING" -> RecipeKind.COOKING;
            default -> RecipeKind.CRAFTING;
        };
    }

    private static String defaultSkill(RecipeKind kind) {
        return switch (kind) {
            case SMITHING -> "smithing";
            case COOKING -> "cooking";
            case CRAFTING -> "crafting";
        };
    }

    private static StationType parseStation(String raw, RecipeKind kind, List<RecipeInput> inputs) {
        if (raw != null && !raw.isBlank()) {
            return switch (raw.trim().toUpperCase(Locale.ROOT)) {
                case "FURNACE" -> StationType.FURNACE;
                case "ANVIL" -> StationType.ANVIL;
                case "CRAFTING_TABLE", "WORKBENCH" -> StationType.CRAFTING_TABLE;
                default -> StationType.defaultFor(kind, inputs);
            };
        }
        return StationType.defaultFor(kind, inputs);
    }

    private static List<RecipeInput> parseInputs(List<Map<?, ?>> rawList) {
        if (rawList == null || rawList.isEmpty()) {
            return List.of();
        }
        List<RecipeInput> inputs = new ArrayList<>();
        for (Map<?, ?> raw : rawList) {
            Object matObj = raw.get("material");
            if (matObj == null) {
                continue;
            }
            Material material = parseMaterial(String.valueOf(matObj), null);
            if (material == null) {
                continue;
            }
            int amount = 1;
            Object amountObj = raw.get("amount");
            if (amountObj instanceof Number n) {
                amount = Math.max(1, n.intValue());
            } else if (amountObj != null) {
                try {
                    amount = Math.max(1, Integer.parseInt(String.valueOf(amountObj)));
                } catch (NumberFormatException ignored) {
                }
            }
            inputs.add(new RecipeInput(material, amount));
        }
        return List.copyOf(inputs);
    }

    private static RecipeOutput parseOutput(ConfigurationSection section) {
        Material material = parseMaterial(section.getString("material"), null);
        if (material == null) {
            return null;
        }
        int amount = section.getInt("amount", 1);
        String displayName = section.getString("display-name");
        String gearTier = section.getString("gear-tier");
        return new RecipeOutput(material, amount, displayName, gearTier);
    }

    public static Material parseMaterial(String raw, Material fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
        return material != null ? material : fallback;
    }

    private static String prettify(String id) {
        String[] parts = id.replace('_', ' ').split(" ");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return sb.toString();
    }
}
