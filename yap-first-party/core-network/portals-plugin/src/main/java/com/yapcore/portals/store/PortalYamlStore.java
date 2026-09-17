package com.yapcore.portals.store;

import com.yapcore.portals.Portal;
import com.yapcore.portals.PortalColors;
import com.yapcore.portals.PortalCuboid;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** YAML persistence for {@code portals.yml}. HEAVY I/O — call off region tick when possible. */
public final class PortalYamlStore {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, Portal> byName = new ConcurrentHashMap<>();

    public PortalYamlStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "portals.yml");
    }

    public void load() {
        if (!file.exists()) {
            plugin.saveResource("portals.yml", false);
        }
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource("portals.yml")) {
            if (in != null) {
                yaml.setDefaults(YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (IOException ignored) {
            // defaults optional
        }
        Map<String, Portal> next = new LinkedHashMap<>();
        ConfigurationSection root = yaml.getConfigurationSection("portals");
        if (root != null) {
            for (String key : root.getKeys(false)) {
                ConfigurationSection sec = root.getConfigurationSection(key);
                if (sec == null) {
                    continue;
                }
                try {
                    Portal portal = readOne(key, sec);
                    next.put(portal.name(), portal);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Bad portal '" + key + "': " + e.getMessage());
                }
            }
        }
        byName.clear();
        byName.putAll(next);
        plugin.getLogger().info("Loaded " + byName.size() + " fleet portal(s)");
    }

    public void saveAll() throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Portal p : byName.values()) {
            String path = "portals." + p.name();
            yaml.set(path + ".enabled", p.enabled());
            yaml.set(path + ".world", p.world());
            yaml.set(path + ".min", List.of(p.cuboid().minX(), p.cuboid().minY(), p.cuboid().minZ()));
            yaml.set(path + ".max", List.of(p.cuboid().maxX(), p.cuboid().maxY(), p.cuboid().maxZ()));
            yaml.set(path + ".target-server", p.targetServer());
            yaml.set(path + ".permission", p.permission());
            yaml.set(path + ".cooldown-seconds", p.cooldownSeconds());
            yaml.set(path + ".message", p.enterMessage());
            yaml.set(path + ".color", p.color());
        }
        if (byName.isEmpty()) {
            yaml.createSection("portals");
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create " + parent);
        }
        yaml.save(file);
    }

    public Collection<Portal> all() {
        return Collections.unmodifiableCollection(new ArrayList<>(byName.values()));
    }

    public Optional<Portal> get(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byName.get(name.trim().toLowerCase(Locale.ROOT)));
    }

    public void put(Portal portal) {
        byName.put(portal.name(), portal);
    }

    public boolean remove(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return byName.remove(name.trim().toLowerCase(Locale.ROOT)) != null;
    }

    public Optional<Portal> findAt(String world, int x, int y, int z) {
        if (world == null) {
            return Optional.empty();
        }
        Optional<Portal> exact = findContaining(world, x, y, z);
        if (exact.isPresent()) {
            return exact;
        }
        // Solid portal glass — fire when the player touches a face of the volume.
        int[][] neighbors = {
                {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
        };
        for (int[] d : neighbors) {
            Optional<Portal> hit = findContaining(world, x + d[0], y + d[1], z + d[2]);
            if (hit.isPresent()) {
                return hit;
            }
        }
        return Optional.empty();
    }

    private Optional<Portal> findContaining(String world, int x, int y, int z) {
        Portal best = null;
        int bestVol = Integer.MAX_VALUE;
        for (Portal p : byName.values()) {
            if (!p.enabled() || !world.equals(p.world())) {
                continue;
            }
            if (!p.cuboid().containsBlock(x, y, z)) {
                continue;
            }
            int vol = p.cuboid().volumeBlocks();
            if (best == null || vol < bestVol) {
                best = p;
                bestVol = vol;
            }
        }
        return Optional.ofNullable(best);
    }

    private static Portal readOne(String key, ConfigurationSection sec) {
        String world = sec.getString("world", "world");
        List<Integer> min = sec.getIntegerList("min");
        List<Integer> max = sec.getIntegerList("max");
        if (min.size() < 3 || max.size() < 3) {
            throw new IllegalArgumentException("min/max need [x,y,z]");
        }
        PortalCuboid cuboid = PortalCuboid.of(
                min.get(0), min.get(1), min.get(2),
                max.get(0), max.get(1), max.get(2));
        String target = sec.getString("target-server", "");
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("target-server required");
        }
        return new Portal(
                key,
                world,
                cuboid,
                target,
                sec.getString("permission", ""),
                sec.getInt("cooldown-seconds", 3),
                sec.getBoolean("enabled", true),
                sec.getString("message", ""),
                sec.getString("color", PortalColors.DEFAULT)
        );
    }
}
