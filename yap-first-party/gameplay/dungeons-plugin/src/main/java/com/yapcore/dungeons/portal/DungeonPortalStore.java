package com.yapcore.dungeons.portal;

import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

/** Survives restarts — dungeon keystone PDC alone is not enough on Folia. */
public final class DungeonPortalStore {

    public record Snap(
            String worldName,
            Axis axis,
            int minAlong,
            int minY,
            int fixed,
            int sizeAlong,
            int height) {

        PortalStructure.Frame toFrame(World world) {
            return axis == Axis.X
                    ? new PortalStructure.Frame(world, minAlong, minY, fixed, sizeAlong, height, axis)
                    : new PortalStructure.Frame(world, fixed, minY, minAlong, sizeAlong, height, axis);
        }

        static Snap from(PortalStructure.Frame frame) {
            return new Snap(
                    frame.world().getName(),
                    frame.axis(),
                    frame.minAlong(),
                    frame.minY(),
                    frame.fixed(),
                    frame.sizeAlong(),
                    frame.height());
        }

        String key() {
            return worldName + "|" + axis.name() + "|" + minAlong + "|" + minY + "|"
                    + fixed + "|" + sizeAlong + "|" + height;
        }
    }

    private final JavaPlugin plugin;
    private final File file;
    private final CopyOnWriteArrayList<Snap> snaps = new CopyOnWriteArrayList<>();

    public DungeonPortalStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "structure-portals.yml");
    }

    public List<Snap> all() {
        return List.copyOf(snaps);
    }

    public void load() {
        snaps.clear();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("portals");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) {
                continue;
            }
            try {
                Axis axis = Axis.valueOf(sec.getString("axis", "X").toUpperCase(Locale.ROOT));
                snaps.add(new Snap(
                        sec.getString("world", "world"),
                        axis,
                        sec.getInt("min-along"),
                        sec.getInt("min-y"),
                        sec.getInt("fixed"),
                        sec.getInt("size", 4),
                        sec.getInt("height", 5)));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Bad dungeon portal snap " + id, e);
            }
        }
        plugin.getLogger().info("Loaded " + snaps.size() + " persisted dungeon structure portal(s)");
    }

    public void upsert(PortalStructure.Frame frame) {
        Snap snap = Snap.from(frame);
        for (Snap existing : snaps) {
            if (existing.key().equals(snap.key())) {
                return;
            }
        }
        snaps.add(snap);
        save();
    }

    public void remove(PortalStructure.Frame frame) {
        String key = Snap.from(frame).key();
        snaps.removeIf(s -> s.key().equals(key));
        save();
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        int i = 0;
        for (Snap snap : snaps) {
            String path = "portals." + i++;
            yaml.set(path + ".world", snap.worldName());
            yaml.set(path + ".axis", snap.axis().name());
            yaml.set(path + ".min-along", snap.minAlong());
            yaml.set(path + ".min-y", snap.minY());
            yaml.set(path + ".fixed", snap.fixed());
            yaml.set(path + ".size", snap.sizeAlong());
            yaml.set(path + ".height", snap.height());
        }
        try {
            File parent = file.getParentFile();
            if (parent != null) {
                //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed saving structure-portals.yml", e);
        }
    }

    public Optional<PortalStructure.Frame> frameAt(World world, int chunkX, int chunkZ) {
        for (Snap snap : snaps) {
            if (!snap.worldName().equals(world.getName())) {
                continue;
            }
            PortalStructure.Frame frame = snap.toFrame(world);
            int minAlong = frame.minAlong();
            int maxAlong = frame.maxAlong();
            int minC = Math.min(minAlong, maxAlong) >> 4;
            int maxC = Math.max(minAlong, maxAlong) >> 4;
            if (frame.axis() == Axis.X) {
                if (chunkZ == (frame.fixed() >> 4) && chunkX >= minC && chunkX <= maxC) {
                    return Optional.of(frame);
                }
            } else if (chunkX == (frame.fixed() >> 4) && chunkZ >= minC && chunkZ <= maxC) {
                return Optional.of(frame);
            }
        }
        return Optional.empty();
    }

    public List<PortalStructure.Frame> framesInLoadedWorlds() {
        List<PortalStructure.Frame> out = new ArrayList<>();
        for (Snap snap : snaps) {
            World world = Bukkit.getWorld(snap.worldName());
            if (world == null) {
                continue;
            }
            out.add(snap.toFrame(world));
        }
        return out;
    }
}
