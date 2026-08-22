package com.yapcore.mechanics.farming;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class FarmingLoader {

    private static final Logger LOG = Logger.getLogger("YaPMechanics");

    public record Drop(Material material, int min, int max) {
    }

    public record CropDef(
            String id,
            Material seed,
            Material cropBlock,
            int matureAge,
            boolean replant,
            List<Drop> drops) {
    }

    private Map<Material, CropDef> byCrop = Map.of();
    private Map<Material, CropDef> bySeed = Map.of();

    public void load(Path file) {
        Map<String, CropDef> loaded = new LinkedHashMap<>();
        if (!Files.isRegularFile(file)) {
            byCrop = Map.of();
            bySeed = Map.of();
            return;
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
            var root = yaml.getConfigurationSection("crops");
            if (root == null) {
                byCrop = Map.of();
                bySeed = Map.of();
                return;
            }
            for (String id : root.getKeys(false)) {
                var row = root.getConfigurationSection(id);
                if (row == null) {
                    continue;
                }
                Material seed = Material.matchMaterial(row.getString("seed", "WHEAT_SEEDS").toUpperCase(Locale.ROOT));
                Material crop = Material.matchMaterial(row.getString("crop-block", "WHEAT").toUpperCase(Locale.ROOT));
                if (seed == null || crop == null) {
                    continue;
                }
                List<Drop> drops = new ArrayList<>();
                for (Map<?, ?> raw : row.getMapList("drops")) {
                    Material mat = Material.matchMaterial(String.valueOf(raw.get("material")).toUpperCase(Locale.ROOT));
                    if (mat == null) {
                        continue;
                    }
                    int min = raw.get("min") instanceof Number n ? n.intValue() : 1;
                    int max = raw.get("max") instanceof Number n ? n.intValue() : min;
                    drops.add(new Drop(mat, min, max));
                }
                loaded.put(id, new CropDef(
                        id,
                        seed,
                        crop,
                        row.getInt("mature-age", 7),
                        row.getBoolean("replant", true),
                        List.copyOf(drops)));
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load farming.yml", e);
        }
        Map<Material, CropDef> cropIndex = new LinkedHashMap<>();
        Map<Material, CropDef> seedIndex = new LinkedHashMap<>();
        for (CropDef def : loaded.values()) {
            cropIndex.put(def.cropBlock(), def);
            seedIndex.put(def.seed(), def);
        }
        byCrop = Map.copyOf(cropIndex);
        bySeed = Map.copyOf(seedIndex);
    }

    public CropDef byCrop(Material crop) {
        return byCrop.get(crop);
    }

    public CropDef bySeed(Material seed) {
        return bySeed.get(seed);
    }
}
