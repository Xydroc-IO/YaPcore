package com.yapcore.portals.enddoor;

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

/**
 * Survives restarts — block PDC on Folia is unreliable for End-door keystones.
 */
public final class EndDoorStore {

    public record Snap(
            String worldName,
            Axis axis,
            int minAlong,
            int minY,
            int fixed,
            int sizeAlong,
            int height) {

        EndDoorStructure.Frame toFrame(World world) {
            return axis == Axis.X
                    ? new EndDoorStructure.Frame(world, minAlong, minY, fixed, sizeAlong, height, axis)
                    : new EndDoorStructure.Frame(world, fixed, minY, minAlong, sizeAlong, height, axis);
        }

        static Snap from(EndDoorStructure.Frame frame) {
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

    public EndDoorStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "end-doors.yml");
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
        ConfigurationSection root = yaml.getConfigurationSection("doors");
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
                plugin.getLogger().log(Level.WARNING, "Bad end-door snap " + id, e);
            }
        }
        plugin.getLogger().info("Loaded " + snaps.size() + " persisted End door(s)");
    }

    public void upsert(EndDoorStructure.Frame frame) {
        Snap snap = Snap.from(frame);
        snaps.removeIf(s -> s.key().equals(snap.key()));
        snaps.add(snap);
        save();
    }

    public void remove(EndDoorStructure.Frame frame) {
        String key = Snap.from(frame).key();
        snaps.removeIf(s -> s.key().equals(key));
        save();
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        int i = 0;
        for (Snap snap : snaps) {
            String path = "doors." + i++;
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
            plugin.getLogger().log(Level.WARNING, "Failed saving end-doors.yml", e);
        }
    }

    public Optional<EndDoorStructure.Frame> frameAt(World world, int chunkX, int chunkZ) {
        for (Snap snap : snaps) {
            if (!snap.worldName().equals(world.getName())) {
                continue;
            }
            EndDoorStructure.Frame frame = snap.toFrame(world);
            int kx = frame.keystone().getX() >> 4;
            int kz = frame.keystone().getZ() >> 4;
            if (kx == chunkX && kz == chunkZ) {
                return Optional.of(frame);
            }
            // Also match if any frame block is in this chunk
            int minCx = Math.min(frame.minAlong(), frame.maxAlong()) >> 4;
            int maxCx = Math.max(frame.minAlong(), frame.maxAlong()) >> 4;
            if (frame.axis() == Axis.X) {
                int fcz = frame.fixed() >> 4;
                if (chunkZ == fcz && chunkX >= minCx && chunkX <= maxCx) {
                    return Optional.of(frame);
                }
            } else {
                int fcx = frame.fixed() >> 4;
                if (chunkX == fcx && chunkZ >= minCx && chunkZ <= maxCx) {
                    return Optional.of(frame);
                }
            }
        }
        return Optional.empty();
    }

    public List<EndDoorStructure.Frame> framesInLoadedWorlds() {
        List<EndDoorStructure.Frame> out = new ArrayList<>();
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
