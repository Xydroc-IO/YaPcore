package com.yapcore.mmocontent.boss;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BossPackLoader {

    private static final Logger LOG = Logger.getLogger("YaPMmoContent");

    private Map<String, BossDefinition> bosses = Map.of();

    /** Load a single YAML file or every {@code *.yml} in a directory. */
    public void load(Path fileOrDir) {
        Map<String, BossDefinition> loaded = new LinkedHashMap<>();
        if (fileOrDir == null) {
            bosses = Map.copyOf(loaded);
            return;
        }
        if (Files.isDirectory(fileOrDir)) {
            try (var stream = Files.list(fileOrDir)) {
                stream.filter(p -> {
                    String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                    return n.endsWith(".yml") || n.endsWith(".yaml");
                }).sorted().forEach(path -> loadFile(path, loaded));
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to list bosses in " + fileOrDir, e);
            }
        } else if (Files.isRegularFile(fileOrDir)) {
            loadFile(fileOrDir, loaded);
        }
        bosses = Map.copyOf(loaded);
    }

    public Map<String, BossDefinition> bosses() {
        return bosses;
    }

    public BossDefinition get(String id) {
        return bosses.get(id);
    }

    private void loadFile(Path file, Map<String, BossDefinition> loaded) {
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
            ConfigurationSection root = yaml.getConfigurationSection("bosses");
            if (root == null) {
                return;
            }
            for (String id : root.getKeys(false)) {
                ConfigurationSection b = root.getConfigurationSection(id);
                if (b == null) {
                    continue;
                }
                EntityType type = EntityType.ZOMBIE;
                try {
                    type = EntityType.valueOf(b.getString("entity", "ZOMBIE").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                }
                List<BossDefinition.LootEntry> loot = new ArrayList<>();
                for (Map<?, ?> raw : b.getMapList("loot")) {
                    loot.add(parseLoot(raw));
                }
                loaded.put(id, new BossDefinition(
                        id,
                        b.getString("display-name", id),
                        type,
                        b.getDouble("health", 100),
                        b.getString("world", "world"),
                        b.getDouble("x", 0),
                        b.getDouble("y", 64),
                        b.getDouble("z", 0),
                        (float) b.getDouble("yaw", 0),
                        Math.max(30, b.getInt("respawn-seconds", 300)),
                        List.copyOf(loot)));
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load bosses from " + file, e);
        }
    }

    private static BossDefinition.LootEntry parseLoot(Map<?, ?> raw) {
        Object cmd = raw.get("command");
        if (cmd != null) {
            return new BossDefinition.LootEntry(null, String.valueOf(cmd));
        }
        Material mat = Material.STONE;
        Object itemRaw = raw.get("item");
        if (itemRaw != null) {
            Material matched = Material.matchMaterial(String.valueOf(itemRaw));
            if (matched != null) {
                mat = matched;
            }
        }
        int amount = 1;
        Object amountRaw = raw.get("amount");
        if (amountRaw instanceof Number n) {
            amount = n.intValue();
        }
        ItemStack stack = new ItemStack(mat, Math.max(1, amount));
        Object nameRaw = raw.get("name");
        if (nameRaw != null) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(String.valueOf(nameRaw).replace('&', '§'));
                stack.setItemMeta(meta);
            }
        }
        return new BossDefinition.LootEntry(stack, null);
    }
}
