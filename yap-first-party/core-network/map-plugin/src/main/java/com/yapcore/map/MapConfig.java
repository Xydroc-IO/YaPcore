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
    private boolean useSpawnOrigin;
    private int originChunkX;
    private int originChunkZ;
    private boolean markersPlayers = true;
    private boolean markersNpcs;
    private boolean markersRegions;
    private boolean markersPois = true;
    private boolean markersClaims;
    private boolean markersFactionColors;
    private int markersPollSeconds = 5;
    private boolean layerSurface = true;
    private boolean layerCave = true;
    private int caveMaxY = 48;
    private boolean biomeTint = true;
    private int retentionMaxAgeDays;
    private int retentionMaxDiskMb;
    private boolean meshEnabled = true;
    /** Optional mesh Y cap; {@code <= 0} means fall back to {@link #maxHeight}. */
    private int meshMaxY;
    private List<String> meshLayers = List.of(MapLayerSampler.LAYER_FULL);
    private String meshDefaultLayer = MapLayerSampler.LAYER_FULL;
    /** Emit stairs/slab/carpet/fence multi-box models. */
    private boolean meshModels = true;
    /** Write .ymesh alongside JSON. */
    private boolean meshBinary = true;
    /** Max LOD to generate (0–2). */
    private int meshMaxLod = 2;
    /**
     * When true, re-center mesh sample origin on average player chunk and expand
     * toward players near the window edge (see {@link #meshExtraRadius}).
     */
    private boolean meshFollowPlayers;
    /** Extra chunks beyond {@link #sampleChunkRadius} when following players. */
    private int meshExtraRadius;

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
        String originMode = c.getString("render-origin.mode", "fixed");
        if ("spawn".equalsIgnoreCase(originMode)) {
            originChunkX = 0;
            originChunkZ = 0;
            useSpawnOrigin = true;
        } else {
            useSpawnOrigin = false;
            originChunkX = c.getInt("render-origin.chunk-x", 0);
            originChunkZ = c.getInt("render-origin.chunk-z", 0);
        }
        markersPlayers = c.getBoolean("markers.players", true);
        markersNpcs = c.getBoolean("markers.npcs", false);
        markersRegions = c.getBoolean("markers.regions", false);
        markersPois = c.getBoolean("markers.pois", true);
        markersClaims = c.getBoolean("markers.claims", false);
        markersFactionColors = c.getBoolean("markers.faction-colors", false);
        markersPollSeconds = Math.max(2, c.getInt("markers.poll-seconds", 5));
        layerSurface = c.getBoolean("layers.surface", true);
        layerCave = c.getBoolean("layers.cave", true);
        caveMaxY = Math.max(-64, c.getInt("layers.cave-max-y", 48));
        biomeTint = c.getBoolean("layers.biome-tint", true);
        retentionMaxAgeDays = Math.max(0, c.getInt("retention.max-age-days", 0));
        retentionMaxDiskMb = Math.max(0, c.getInt("retention.max-disk-mb", 0));
        meshEnabled = c.getBoolean("mesh.enabled", true);
        meshMaxY = c.getInt("mesh.max-y", 0);
        meshLayers = parseMeshLayers(c);
        meshDefaultLayer = MapLayerSampler.normalizeMeshLayer(
                c.getString("mesh.default-layer", meshLayers.isEmpty()
                        ? MapLayerSampler.LAYER_FULL
                        : meshLayers.get(0)));
        if (!meshLayers.contains(meshDefaultLayer)) {
            meshDefaultLayer = meshLayers.get(0);
        }
        meshModels = c.getBoolean("mesh.models", true);
        meshBinary = c.getBoolean("mesh.binary", true);
        meshMaxLod = Math.max(0, Math.min(2, c.getInt("mesh.max-lod", 2)));
        meshFollowPlayers = c.getBoolean("mesh.follow-players", false);
        meshExtraRadius = Math.max(0, c.getInt("mesh.extra-radius", 0));
    }

    private List<String> parseMeshLayers(FileConfiguration c) {
        List<String> configured = c.getStringList("mesh.layers");
        if (configured != null && !configured.isEmpty()) {
            List<String> out = new ArrayList<>();
            for (String raw : configured) {
                String n = MapLayerSampler.normalizeMeshLayer(raw);
                if (!out.contains(n)) {
                    out.add(n);
                }
            }
            return out.isEmpty() ? List.of(MapLayerSampler.LAYER_FULL) : List.copyOf(out);
        }
        // Singular mesh.layer: surface | cave | full
        String singular = c.getString("mesh.layer", null);
        if (singular != null && !singular.isBlank()) {
            return List.of(MapLayerSampler.normalizeMeshLayer(singular));
        }
        return List.of(MapLayerSampler.LAYER_FULL);
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

    public boolean useSpawnOrigin() {
        return useSpawnOrigin;
    }

    public int originChunkX() {
        return originChunkX;
    }

    public int originChunkZ() {
        return originChunkZ;
    }

    /** Resolve spawn-based origin for a world (call on render thread / sync). */
    public void applySpawnOrigin(org.bukkit.World world) {
        if (!useSpawnOrigin || world == null) {
            return;
        }
        var spawn = world.getSpawnLocation();
        originChunkX = spawn.getBlockX() >> 4;
        originChunkZ = spawn.getBlockZ() >> 4;
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

    public boolean markersPois() {
        return markersPois;
    }

    public boolean markersClaims() {
        return markersClaims;
    }

    public boolean markersFactionColors() {
        return markersFactionColors;
    }

    public int markersPollSeconds() {
        return markersPollSeconds;
    }

    public boolean layerSurface() {
        return layerSurface;
    }

    public boolean layerCave() {
        return layerCave;
    }

    public int caveMaxY() {
        return caveMaxY;
    }

    public boolean biomeTint() {
        return biomeTint;
    }

    public int retentionMaxAgeDays() {
        return retentionMaxAgeDays;
    }

    public int retentionMaxDiskMb() {
        return retentionMaxDiskMb;
    }

    public boolean meshEnabled() {
        return meshEnabled;
    }

    /** Effective mesh Y cap (falls back to {@link #maxHeight} when unset). */
    public int meshMaxY() {
        return meshMaxY > 0 ? meshMaxY : maxHeight;
    }

    /** Mesh layers to generate (full / surface / cave). */
    public List<String> enabledMeshLayers() {
        return meshLayers;
    }

    public String meshDefaultLayer() {
        return meshDefaultLayer;
    }

    public boolean meshModels() {
        return meshModels;
    }

    public boolean meshBinary() {
        return meshBinary;
    }

    public int meshMaxLod() {
        return meshMaxLod;
    }

    public boolean meshFollowPlayers() {
        return meshFollowPlayers;
    }

    public int meshExtraRadius() {
        return meshExtraRadius;
    }

    /**
     * Effective mesh sample radius (base + optional follow extra).
     */
    public int meshSampleRadius() {
        return sampleChunkRadius + Math.max(0, meshExtraRadius);
    }

    /**
     * Chunks in the mesh sample window. When {@code overrideOrigin} is set (follow-players),
     * uses that origin with {@link #meshSampleRadius()}; otherwise matches flat tiles.
     */
    public List<int[]> meshSampleChunks(Integer overrideOriginX, Integer overrideOriginZ) {
        int ox = overrideOriginX != null ? overrideOriginX : originChunkX;
        int oz = overrideOriginZ != null ? overrideOriginZ : originChunkZ;
        int radius = meshSampleRadius();
        List<int[]> out = new ArrayList<>();
        for (int dx = 0; dx < radius; dx++) {
            for (int dz = 0; dz < radius; dz++) {
                out.add(new int[] {dx, dz, ox + dx, oz + dz});
            }
        }
        return out;
    }

    /** Enabled flat render layers in UI order (surface first). */
    public List<String> enabledLayers() {
        List<String> out = new ArrayList<>();
        if (layerSurface) {
            out.add(TileRenderer.LAYER_SURFACE);
        }
        if (layerCave) {
            out.add(TileRenderer.LAYER_CAVE);
        }
        if (out.isEmpty()) {
            out.add(TileRenderer.LAYER_SURFACE);
        }
        return out;
    }

    public List<int[]> sampleChunks() {
        List<int[]> out = new ArrayList<>();
        for (int dx = 0; dx < sampleChunkRadius; dx++) {
            for (int dz = 0; dz < sampleChunkRadius; dz++) {
                // [tileX, tileZ, worldChunkX, worldChunkZ]
                out.add(new int[] {dx, dz, originChunkX + dx, originChunkZ + dz});
            }
        }
        return out;
    }
}
