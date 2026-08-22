package com.yapcore.mechanics.node;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ResourceNodeLoader {

    private static final Logger LOG = Logger.getLogger("YaPMechanics");

    public record ResourceNode(
            String id,
            String world,
            int x,
            int y,
            int z,
            Material active,
            Material depleted,
            int respawnSeconds,
            double fishingBonus) {
    }

    private Map<String, ResourceNode> nodes = Map.of();
    private Map<Long, ResourceNode> byBlock = Map.of();

    public void load(Path file) {
        Map<String, ResourceNode> loaded = new LinkedHashMap<>();
        Map<Long, ResourceNode> index = new LinkedHashMap<>();
        if (!Files.isRegularFile(file)) {
            nodes = Map.copyOf(loaded);
            byBlock = Map.copyOf(index);
            return;
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
            var root = yaml.getConfigurationSection("nodes");
            if (root == null) {
                nodes = Map.copyOf(loaded);
                byBlock = Map.copyOf(index);
                return;
            }
            for (String id : root.getKeys(false)) {
                var row = root.getConfigurationSection(id);
                if (row == null) {
                    continue;
                }
                Material active = Material.matchMaterial(row.getString("active", "STONE").toUpperCase(Locale.ROOT));
                Material depleted = Material.matchMaterial(row.getString("depleted", "STONE").toUpperCase(Locale.ROOT));
                if (active == null) {
                    active = Material.STONE;
                }
                if (depleted == null) {
                    depleted = Material.STONE;
                }
                ResourceNode node = new ResourceNode(
                        id,
                        row.getString("world", "world"),
                        row.getInt("x"),
                        row.getInt("y"),
                        row.getInt("z"),
                        active,
                        depleted,
                        Math.max(1, row.getInt("respawn-seconds", 30)),
                        row.getDouble("fishing-bonus", 1.0));
                loaded.put(id, node);
                index.put(pack(node.x(), node.y(), node.z()), node);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load nodes.yml", e);
        }
        nodes = Map.copyOf(loaded);
        byBlock = Map.copyOf(index);
    }

    public Map<String, ResourceNode> nodes() {
        return nodes;
    }

    public ResourceNode at(int x, int y, int z) {
        return byBlock.get(pack(x, y, z));
    }

    public ResourceNode fishingBonusAt(org.bukkit.Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return null;
        }
        for (ResourceNode node : nodes.values()) {
            if (!node.world().equals(loc.getWorld().getName())) {
                continue;
            }
            if (Math.abs(node.x() - loc.getBlockX()) <= 2
                    && Math.abs(node.y() - loc.getBlockY()) <= 2
                    && Math.abs(node.z() - loc.getBlockZ()) <= 2
                    && node.fishingBonus() > 1.0) {
                return node;
            }
        }
        return null;
    }

    private static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) y & 0xFFFL) << 26 | ((long) z & 0x3FFFFFFL);
    }
}
