package com.yapcore.map;

import com.yapcore.sched.YapSched;
import com.yapcore.sched.YapTask;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class MapPlugin extends JavaPlugin implements CommandExecutor, TabCompleter {

    private MapConfig config;
    private TileRenderer renderer;
    private ChunkMeshRenderer meshRenderer;
    private MapHttpServer httpServer;
    private YapTask renderTask;
    private YapTask dirtyTask;
    private YapTask markersTask;
    private MapDirtyListener dirtyListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        MapTelemetry.bind(getDataFolder().toPath());
        ensurePoiExample();
        reloadMap();
        try {
            extractWebAssets();
        } catch (IOException e) {
            getLogger().warning("Could not extract map web assets: " + e.getMessage());
        }

        if (config.useYapcoreServer()) {
            getLogger().info("Map HTTP served by YaPcore pack server — use /map/ on resource-pack-http-port");
        } else {
            startEmbeddedHttp();
        }

        long periodTicks = Math.max(20L, config.renderIntervalMinutes() * 60L * 20L);
        renderTask = YapSched.globalTimer(this, () -> new MapRenderTask(this, config, renderer, meshRenderer).run(),
                100L, periodTicks);
        YapSched.globalLater(this, () -> new MapRenderTask(this, config, renderer, meshRenderer).run(), 40L);
        dirtyTask = YapSched.globalTimer(this, this::flushDirty, 200L, 200L);
        dirtyListener = new MapDirtyListener(renderer, meshRenderer);
        getServer().getPluginManager().registerEvents(dirtyListener, this);
        long markerTicks = Math.max(40L, config.markersPollSeconds() * 20L);
        markersTask = YapSched.globalTimer(this, this::writeMarkersFile, 60L, markerTicks);
        YapSched.globalLater(this, this::writeMarkersFile, 40L);

        var cmd = getCommand("yapmap");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }

        MapTelemetry.refreshDiskBytes(tilesRoot(), meshesRoot());
        getLogger().info("YaPMap ready — tiles in " + tilesRoot().toAbsolutePath()
                + " meshes=" + config.meshEnabled()
                + " layers=" + config.enabledLayers());
    }

    private void ensurePoiExample() {
        Path poi = getDataFolder().toPath().resolve("poi.json");
        if (Files.isRegularFile(poi)) {
            return;
        }
        try {
            Files.createDirectories(poi.getParent());
            try (InputStream in = MapPlugin.class.getResourceAsStream("/poi.json")) {
                if (in != null) {
                    Files.copy(in, poi);
                }
            }
        } catch (IOException e) {
            getLogger().warning("Could not write example poi.json: " + e.getMessage());
        }
    }

    private void startEmbeddedHttp() {
        int port = config.port();
        try {
            httpServer = new MapHttpServer(config.bindHost(), port, tilesRoot(), meshesRoot(),
                    () -> MapMarkers.toJson(config, getDataFolder().toPath()));
            httpServer.start();
            getLogger().info("Map HTTP on http://" + config.bindHost() + ":" + port + "/map/");
        } catch (IOException e) {
            if (port == 8081) {
                try {
                    httpServer = new MapHttpServer(config.bindHost(), 8082, tilesRoot(), meshesRoot(),
                            () -> MapMarkers.toJson(config, getDataFolder().toPath()));
                    httpServer.start();
                    getLogger().warning("Port 8081 in use (YaPcore pack HTTP?) — map HTTP on :8082 instead. "
                            + "Set http.use-yapcore-server: true to share the pack port.");
                    return;
                } catch (IOException retry) {
                    e = retry;
                }
            }
            getLogger().severe("Map HTTP server failed (rendering still active): " + e.getMessage()
                    + " — set http.use-yapcore-server: true in config.yml");
        }
    }

    public void reloadMap() {
        if (config == null) {
            config = new MapConfig(this);
        }
        config.reload();
        MapBlockColors colors = new MapBlockColors();
        renderer = new TileRenderer(config, tilesRoot(), colors);
        meshRenderer = new ChunkMeshRenderer(config, meshesRoot(), colors);
        if (dirtyListener != null) {
            dirtyListener.setRenderers(renderer, meshRenderer);
        }
        try {
            extractWebAssets();
        } catch (IOException e) {
            getLogger().warning("Could not refresh map web assets: " + e.getMessage());
        }
        if (!config.useYapcoreServer()) {
            restartEmbeddedHttp();
        }
    }

    private void restartEmbeddedHttp() {
        if (httpServer != null) {
            httpServer.stop();
            httpServer = null;
        }
        startEmbeddedHttp();
    }

    private void flushDirty() {
        if (renderer == null) {
            return;
        }
        int dirty = renderer.dirtyCount();
        if (meshRenderer != null) {
            dirty = Math.max(dirty, meshRenderer.dirtyCount());
        }
        if (dirty == 0) {
            return;
        }
        MapTelemetry.setStatus("dirty-flush");
        MapTelemetry.updateDirty(dirty);
        for (String worldName : config.worlds()) {
            var world = org.bukkit.Bukkit.getWorld(worldName);
            if (world != null) {
                renderer.renderDirty(this, world);
                if (meshRenderer != null && config.meshEnabled()) {
                    meshRenderer.renderDirty(this, world);
                }
            }
        }
        int remaining = renderer.dirtyCount();
        if (meshRenderer != null) {
            remaining = Math.max(remaining, meshRenderer.dirtyCount());
        }
        MapTelemetry.markRenderComplete(tilesRoot(), meshesRoot(), remaining);
    }

    private void extractWebAssets() throws IOException {
        Path web = webRoot();
        Files.createDirectories(web);
        copyResource("index.html", web.resolve("index.html"));
        copyResource("map.js", web.resolve("map.js"));
        copyResource("map-3d.js", web.resolve("map-3d.js"));
        String layersJson = config.enabledLayers().stream()
                .map(MapPlugin::jsonString)
                .collect(Collectors.joining(","));
        String meshLayersJson = config.enabledMeshLayers().stream()
                .map(MapPlugin::jsonString)
                .collect(Collectors.joining(","));
        String cfg = "window.YAP_MAP_CONFIG={"
                + "sampleChunkRadius:" + config.sampleChunkRadius() + ","
                + "maxZoom:" + TileRenderer.MAX_ZOOM + ","
                + "originBlockX:" + (config.originChunkX() * 16) + ","
                + "originBlockZ:" + (config.originChunkZ() * 16) + ","
                + "originChunkX:" + config.originChunkX() + ","
                + "originChunkZ:" + config.originChunkZ() + ","
                + "defaultWorld:" + jsonString(config.worlds().isEmpty() ? "world" : config.worlds().get(0)) + ","
                + "worlds:[" + config.worlds().stream().map(MapPlugin::jsonString)
                .collect(Collectors.joining(",")) + "],"
                + "layers:[" + layersJson + "],"
                + "defaultLayer:" + jsonString(config.enabledLayers().get(0)) + ","
                + "meshLayers:[" + meshLayersJson + "],"
                + "meshDefaultLayer:" + jsonString(config.meshDefaultLayer()) + ","
                + "meshEnabled:" + config.meshEnabled() + ","
                + "meshBinary:" + config.meshBinary() + ","
                + "meshMaxLod:" + config.meshMaxLod() + ","
                + "meshFollowPlayers:" + config.meshFollowPlayers() + ","
                + "meshExtraRadius:" + config.meshExtraRadius()
                + "};\n";
        Files.writeString(web.resolve("map-config.js"), cfg, StandardCharsets.UTF_8);
        writeMarkersFile();
    }

    private static String jsonString(String s) {
        if (s == null) {
            return "\"\"";
        }
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private void writeMarkersFile() {
        try {
            Path web = webRoot();
            Files.createDirectories(web);
            Files.writeString(web.resolve("markers.json"), MapMarkers.toJson(config, getDataFolder().toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            getLogger().warning("Could not write map markers.json: " + e.getMessage());
        }
    }

    private void copyResource(String name, Path dest) throws IOException {
        try (InputStream in = MapPlugin.class.getResourceAsStream("/map/" + name)) {
            if (in == null) {
                getLogger().warning("Missing map resource /map/" + name);
                return;
            }
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public Path tilesRoot() {
        return getDataFolder().toPath().resolve("map/tiles");
    }

    public Path meshesRoot() {
        return getDataFolder().toPath().resolve("map/meshes");
    }

    public Path webRoot() {
        return getDataFolder().toPath().resolve("web");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yapmap.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length >= 1 && "reload".equalsIgnoreCase(args[0])) {
            reloadMap();
            sender.sendMessage("§aYaPMap reloaded.");
            return true;
        }
        if (args.length >= 1 && "render".equalsIgnoreCase(args[0])) {
            new MapRenderTask(this, config, renderer, meshRenderer).run();
            sender.sendMessage("§aMap render queued.");
            return true;
        }
        if (args.length >= 1 && "prune".equalsIgnoreCase(args[0])) {
            int age = config.retentionMaxAgeDays();
            int disk = config.retentionMaxDiskMb();
            if (args.length >= 2) {
                try {
                    age = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cUsage: /yapmap prune [max-age-days]");
                    return true;
                }
            }
            if (age <= 0 && disk <= 0) {
                sender.sendMessage("§eNo prune policy — set retention.max-age-days / max-disk-mb or pass days.");
                return true;
            }
            try {
                TilePruner.Result tiles = TilePruner.prune(tilesRoot(), age, disk);
                MeshPruner.Result meshes = MeshPruner.prune(meshesRoot(), age, disk);
                rebuildMeshManifestsAfterPrune(meshes);
                MapTelemetry.refreshDiskBytes(tilesRoot(), meshesRoot());
                sender.sendMessage("§aPruned §f" + tiles.deletedFiles() + "§a tiles (§f"
                        + (tiles.freedBytes() / 1024) + "§a KiB) and §f" + meshes.deletedFiles()
                        + "§a mesh files (§f" + (meshes.freedBytes() / 1024) + "§a KiB).");
            } catch (IOException e) {
                sender.sendMessage("§cPrune failed: " + e.getMessage());
            }
            return true;
        }
        sender.sendMessage("§e/yapmap reload|render|prune");
        return true;
    }

    private void rebuildMeshManifestsAfterPrune(MeshPruner.Result meshes) {
        if (meshes == null || meshes.affectedWorldLayers().isEmpty() || config == null) {
            return;
        }
        for (String key : meshes.affectedWorldLayers()) {
            int sep = key.indexOf('|');
            if (sep <= 0) {
                continue;
            }
            String world = key.substring(0, sep);
            String layer = key.substring(sep + 1);
            try {
                MeshEncoder.writeManifest(meshesRoot(), world, layer,
                        config.originChunkX(), config.originChunkZ(), config.meshSampleRadius(),
                        config.meshMaxLod());
            } catch (IOException e) {
                getLogger().warning("Mesh manifest rebuild after prune failed for "
                        + world + "/" + layer + ": " + e.getMessage());
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("yapmap.admin") || args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String s : List.of("reload", "render", "prune")) {
            if (s.startsWith(prefix)) {
                out.add(s);
            }
        }
        return out;
    }

    @Override
    public void onDisable() {
        if (renderTask != null) {
            renderTask.cancel();
        }
        if (dirtyTask != null) {
            dirtyTask.cancel();
        }
        if (markersTask != null) {
            markersTask.cancel();
        }
        if (httpServer != null) {
            httpServer.stop();
        }
    }
}
