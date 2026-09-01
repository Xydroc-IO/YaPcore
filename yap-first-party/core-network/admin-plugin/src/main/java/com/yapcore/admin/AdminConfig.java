package com.yapcore.admin;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

public final class AdminConfig {

    public record ItemPreset(String id, Material material, int amount, String displayName) {
    }

    private List<String> kits = List.of("starter", "adventurer", "vip");
    private List<Integer> moneyAmounts = List.of(100, 1000, 10000, 100000);
    private List<String> broadcastPresets = List.of();
    private List<ItemPreset> presets = List.of();

    public void reload(FileConfiguration c) {
        kits = List.copyOf(c.getStringList("kits"));
        List<Integer> money = new ArrayList<>();
        for (int n : c.getIntegerList("money-amounts")) {
            if (n > 0) {
                money.add(n);
            }
        }
        moneyAmounts = money.isEmpty() ? List.of(100, 1000, 10000) : List.copyOf(money);
        broadcastPresets = List.copyOf(c.getStringList("broadcast-presets"));

        List<ItemPreset> loaded = new ArrayList<>();
        List<?> raw = c.getList("presets");
        if (raw != null) {
            for (Object entry : raw) {
                if (!(entry instanceof ConfigurationSection section)
                        && !(entry instanceof java.util.Map<?, ?>)) {
                    continue;
                }
                ConfigurationSection sec;
                if (entry instanceof ConfigurationSection cs) {
                    sec = cs;
                } else {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> map = (java.util.Map<String, Object>) entry;
                    sec = new org.bukkit.configuration.MemoryConfiguration();
                    for (var e : map.entrySet()) {
                        sec.set(e.getKey(), e.getValue());
                    }
                }
                String id = sec.getString("id", "");
                String matName = sec.getString("material", "");
                Material mat = Material.matchMaterial(matName);
                if (mat == null || !mat.isItem() || id.isBlank()) {
                    continue;
                }
                int amount = Math.max(1, sec.getInt("amount", 1));
                String name = sec.getString("name", id);
                loaded.add(new ItemPreset(id, mat, amount, name));
            }
        }
        presets = List.copyOf(loaded);
    }

    public List<String> kits() {
        return kits;
    }

    public List<Integer> moneyAmounts() {
        return moneyAmounts;
    }

    public List<String> broadcastPresets() {
        return broadcastPresets;
    }

    public List<ItemPreset> presets() {
        return presets;
    }
}
