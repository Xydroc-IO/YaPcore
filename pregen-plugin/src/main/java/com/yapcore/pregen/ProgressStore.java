package com.yapcore.pregen;

import com.yapcore.pregen.shape.ChunkPos;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class ProgressStore {

    private final File dir;
    private final Logger log;

    public ProgressStore(File dataFolder, Logger log) {
        this.dir = new File(dataFolder, "progress");
        this.log = log;
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
    }

    public void save(PregenJob job) {
        File f = file(job.worldName());
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("world", job.worldName());
        yml.set("shape", job.shapeDescription());
        yml.set("total", job.total());
        yml.set("done", job.done());
        yml.set("state", job.state().name());
        List<String> rem = new ArrayList<>();
        for (ChunkPos p : job.snapshotRemaining()) {
            rem.add(p.x() + "," + p.z());
        }
        yml.set("remaining", rem);
        try {
            yml.save(f);
        } catch (IOException e) {
            log.warning("Could not save pregen progress for " + job.worldName() + ": " + e.getMessage());
        }
    }

    public void delete(String worldName) {
        //noinspection ResultOfMethodCallIgnored
        file(worldName).delete();
    }

    public record SavedJob(String world, String shape, long total, int done, List<ChunkPos> remaining) {
    }

    public List<SavedJob> loadAll() {
        List<SavedJob> out = new ArrayList<>();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) {
            return out;
        }
        for (File f : files) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
            String world = yml.getString("world");
            if (world == null) {
                continue;
            }
            List<String> rem = yml.getStringList("remaining");
            List<ChunkPos> coords = new ArrayList<>();
            for (String s : rem) {
                String[] p = s.split(",");
                if (p.length == 2) {
                    try {
                        coords.add(new ChunkPos(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim())));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            if (coords.isEmpty()) {
                continue;
            }
            out.add(new SavedJob(
                    world,
                    yml.getString("shape", "resumed"),
                    yml.getLong("total", coords.size()),
                    yml.getInt("done", 0),
                    coords));
        }
        return out;
    }

    private File file(String world) {
        return new File(dir, world.replaceAll("[^a-zA-Z0-9_\\-]", "_") + ".yml");
    }
}
