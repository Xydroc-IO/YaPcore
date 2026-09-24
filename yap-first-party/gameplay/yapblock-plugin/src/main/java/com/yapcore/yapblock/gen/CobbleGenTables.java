package com.yapcore.yapblock.gen;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Weighted cobble-gen outcome tables by tier. */
public final class CobbleGenTables {

    private final Map<Integer, List<Weighted>> tiers = new HashMap<>();

    public void reload(JavaPlugin plugin) {
        tiers.clear();
        File file = new File(plugin.getDataFolder(), "generators.yml");
        if (!file.exists()) {
            plugin.saveResource("generators.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("tiers");
        if (root == null) {
            putDefault(0);
            return;
        }
        for (String key : root.getKeys(false)) {
            int tier;
            try {
                tier = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                continue;
            }
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null) {
                continue;
            }
            List<Weighted> list = new ArrayList<>();
            for (String matName : sec.getKeys(false)) {
                Material mat;
                try {
                    mat = Material.valueOf(matName.toUpperCase());
                } catch (IllegalArgumentException e) {
                    continue;
                }
                int weight = Math.max(0, sec.getInt(matName, 0));
                if (weight > 0) {
                    list.add(new Weighted(mat, weight));
                }
            }
            if (!list.isEmpty()) {
                tiers.put(tier, List.copyOf(list));
            }
        }
        if (tiers.isEmpty()) {
            putDefault(0);
        }
    }

    private void putDefault(int tier) {
        tiers.put(tier, List.of(
                new Weighted(Material.COBBLESTONE, 80),
                new Weighted(Material.STONE, 20)));
    }

    public Material roll(int tier) {
        List<Weighted> list = tiers.get(tier);
        if (list == null || list.isEmpty()) {
            list = tiers.getOrDefault(0, List.of(new Weighted(Material.COBBLESTONE, 1)));
        }
        int total = 0;
        for (Weighted w : list) {
            total += w.weight;
        }
        int roll = ThreadLocalRandom.current().nextInt(Math.max(1, total));
        int acc = 0;
        for (Weighted w : list) {
            acc += w.weight;
            if (roll < acc) {
                return w.material;
            }
        }
        return Material.COBBLESTONE;
    }

    private record Weighted(Material material, int weight) {
    }
}
