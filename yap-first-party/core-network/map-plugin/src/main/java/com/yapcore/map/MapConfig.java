package com.yapcore.map;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class MapConfig {

    private final JavaPlugin plugin;
    private String bindHost = "127.0.0.1";
    private int port = 8082;
    private boolean useYapcoreServer = true;
    private List<String> worlds = List.of("world");
    private int renderIntervalMinutes = 15;
    private int maxHeight = 320;
    private int sampleChunkRadius = 8;
    private boolean markersPlayers = true;
    private boolean markersNpcs;
    private boolean markersRegions;
    private int markersPollSeconds = 5;

    public MapConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        if (plugin == null) {
            return;
        }
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        bindHost = c.getString("http.bind", bindHost);
        port = Math.max(1, Math.min(65535, c.getInt("http.port", port)));
        useYapcoreServer = c.getBoolean("http.use-yapcore-server", useYapcoreServer);
        List<String> configured = c.getStringList("worlds");
        if (configured == null || configured.isEmpty()) {
            worlds = List.of("world");
        } else {
            worlds = List.copyOf(configured);
        }
        renderIntervalMinutes = Math.max(1, c.getInt("render-interval-minutes", renderIntervalMinutes));
        maxHeight = Math.max(16, c.getInt("max-height", maxHeight));
        sampleChunkRadius = Math.max(1, c.getInt("sample-chunk-radius", sampleChunkRadius));
        markersPlayers = c.getBoolean("markers.players", true);
        markersNpcs = c.getBoolean("markers.npcs", false);
        markersRegions = c.getBoolean("markers.regions", false);
        markersPollSeconds = Math.max(2, c.getInt("markers.poll-seconds", 5));
    }

    public String bindHost() {
        return bindHost;
    }

    public int port() {
        return port;
    }

    public boolean useYapcoreServer() {
        return useYapcoreServer;
    }

    public List<String> worlds() {
        return worlds;
    }

    public int renderIntervalMinutes() {
        return renderIntervalMinutes;
    }

    public int maxHeight() {
        return maxHeight;
    }

    public int sampleChunkRadius() {
        return sampleChunkRadius;
    }

    public boolean markersPlayers() {
        return markersPlayers;
    }

    public boolean markersNpcs() {
        return markersNpcs;
    }

    public boolean markersRegions() {
        return markersRegions;
    }

    public int markersPollSeconds() {
        return markersPollSeconds;
    }

    public List<int[]> sampleChunks() {
        List<int[]> out = new ArrayList<>();
        for (int x = 0; x < sampleChunkRadius; x++) {
            for (int z = 0; z < sampleChunkRadius; z++) {
                out.add(new int[] {x, z});
            }
        }
        return out;
    }
}
