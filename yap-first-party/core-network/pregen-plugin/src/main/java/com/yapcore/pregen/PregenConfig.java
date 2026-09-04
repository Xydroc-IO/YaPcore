package com.yapcore.pregen;

import com.yapcore.pregen.shape.ChunkPos;
import org.bukkit.configuration.file.FileConfiguration;

public final class PregenConfig {

    private int chunksPerTick = 5;
    private double maxMspt = 40.0;
    private int broadcastIntervalSec = 30;
    private boolean autoResume = true;
    private int maxWorlds = 4;
    private int maxInflight = 32;
    private int maxInflightPerRegion = 8;

    public void load(FileConfiguration cfg) {
        chunksPerTick = Math.max(1, cfg.getInt("chunks-per-tick", 5));
        maxMspt = Math.max(5.0, cfg.getDouble("max-mspt", 40.0));
        broadcastIntervalSec = Math.max(5, cfg.getInt("broadcast-interval-sec", 30));
        autoResume = cfg.getBoolean("auto-resume", true);
        maxWorlds = Math.max(1, cfg.getInt("max-worlds", 4));
        maxInflight = Math.max(1, cfg.getInt("max-inflight", 32));
        maxInflightPerRegion = Math.max(1, cfg.getInt("max-inflight-per-region", 8));
    }

    public int chunksPerTick() {
        return chunksPerTick;
    }

    public double maxMspt() {
        return maxMspt;
    }

    public int broadcastIntervalSec() {
        return broadcastIntervalSec;
    }

    public boolean autoResume() {
        return autoResume;
    }

    public int maxWorlds() {
        return maxWorlds;
    }

    public int maxInflight() {
        return maxInflight;
    }

    public int maxInflightPerRegion() {
        return maxInflightPerRegion;
    }
}
