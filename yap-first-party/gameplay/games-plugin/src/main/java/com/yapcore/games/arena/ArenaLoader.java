package com.yapcore.games.arena;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ArenaLoader {

    private static final Logger LOG = Logger.getLogger("YaPGames");

    private final Path arenasDir;
    private Map<String, ArenaDefinition> arenas = Map.of();

    public ArenaLoader(Path arenasDir) {
        this.arenasDir = arenasDir;
    }

    public void reload() {
        Map<String, ArenaDefinition> loaded = new LinkedHashMap<>();
        if (!Files.isDirectory(arenasDir)) {
            arenas = Map.copyOf(loaded);
            return;
        }
        try (var stream = Files.list(arenasDir)) {
            stream.filter(p -> {
                String n = p.getFileName().toString().toLowerCase();
                return n.endsWith(".yml") || n.endsWith(".yaml");
            }).forEach(path -> loadFile(path, loaded));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to list arenas in " + arenasDir, e);
        }
        arenas = Map.copyOf(loaded);
    }

    public Map<String, ArenaDefinition> arenas() {
        return arenas;
    }

    public ArenaDefinition get(String id) {
        return arenas.get(id);
    }

    static ArenaDefinition parseArena(String id, ConfigurationSection section) {
        String regionName = section.getString("region");
        if (regionName != null && !regionName.isBlank()) {
            ArenaDefinition fromRegion = resolveRegion(id, regionName, section);
            if (fromRegion != null) {
                return fromRegion;
            }
        }
        String worldName = section.getString("world", "world");
        ConfigurationSection min = section.getConfigurationSection("min");
        ConfigurationSection max = section.getConfigurationSection("max");
        if (min == null || max == null) {
            return null;
        }
        int minX = min.getInt("x");
        int minY = min.getInt("y", 0);
        int minZ = min.getInt("z");
        int maxX = max.getInt("x");
        int maxY = max.getInt("y", 255);
        int maxZ = max.getInt("z");

        List<Location> spawns = new ArrayList<>();
        List<Map<?, ?>> rawSpawns = section.getMapList("spawns");
        for (Map<?, ?> raw : rawSpawns) {
            Location loc = parseLocation(worldName, raw);
            if (loc != null) {
                spawns.add(loc);
            }
        }
        Location lobby = parseLocation(worldName, section.getConfigurationSection("lobby"));
        return new ArenaDefinition(id, worldName, minX, minY, minZ, maxX, maxY, maxZ, List.copyOf(spawns), lobby);
    }

    private static ArenaDefinition resolveRegion(String id, String regionName, ConfigurationSection section) {
        try {
            var regions = com.yapcore.regions.RegionServices.find();
            if (regions.isEmpty()) {
                return null;
            }
            var region = regions.get().named(regionName);
            if (region.isEmpty()) {
                LOG.warning("Arena " + id + ": unknown region " + regionName);
                return null;
            }
            var r = region.get();
            List<Location> spawns = new ArrayList<>();
            for (Map<?, ?> raw : section.getMapList("spawns")) {
                Location loc = parseLocation(r.world(), raw);
                if (loc != null) {
                    spawns.add(loc);
                }
            }
            Location lobby = parseLocation(r.world(), section.getConfigurationSection("lobby"));
            return new ArenaDefinition(
                    id, r.world(),
                    r.minX(), r.minY(), r.minZ(),
                    r.maxX(), r.maxY(), r.maxZ(),
                    List.copyOf(spawns), lobby);
        } catch (Throwable t) {
            LOG.warning("Arena " + id + ": region resolve failed: " + t.getMessage());
            return null;
        }
    }

    private void loadFile(Path path, Map<String, ArenaDefinition> loaded) {
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
            ConfigurationSection root = yaml.getConfigurationSection("arenas");
            if (root == null) {
                return;
            }
            for (String arenaId : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(arenaId);
                if (section == null) {
                    continue;
                }
                ArenaDefinition def = parseArena(arenaId, section);
                if (def != null) {
                    loaded.put(arenaId, def);
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load arena file " + path, e);
        }
    }

    static Location parseLocation(String worldName, ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        return parseLocation(worldName, section.getValues(false));
    }

    static Location parseLocation(String worldName, Map<?, ?> raw) {
        if (raw == null) {
            return null;
        }
        Object xObj = raw.get("x");
        Object zObj = raw.get("z");
        if (xObj == null || zObj == null) {
            return null;
        }
        double x = toDouble(xObj);
        double y = raw.containsKey("y") ? toDouble(raw.get("y")) : 64;
        double z = toDouble(zObj);
        float yaw = raw.containsKey("yaw") ? (float) toDouble(raw.get("yaw")) : 0f;
        float pitch = raw.containsKey("pitch") ? (float) toDouble(raw.get("pitch")) : 0f;
        org.bukkit.World world = null;
        try {
            world = Bukkit.getWorld(worldName);
        } catch (Throwable ignored) {
        }
        return new Location(world, x, y, z, yaw, pitch);
    }

    private static double toDouble(Object obj) {
        if (obj instanceof Number n) {
            return n.doubleValue();
        }
        return Double.parseDouble(String.valueOf(obj));
    }
}
