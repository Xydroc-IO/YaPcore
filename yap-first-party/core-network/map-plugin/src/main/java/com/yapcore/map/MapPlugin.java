package com.yapcore.map;

import com.yapcore.sched.YapSched;
import com.yapcore.sched.YapTask;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class MapPlugin extends JavaPlugin implements CommandExecutor {

    private MapConfig config;
    private TileRenderer renderer;
    private MapHttpServer httpServer;
    private YapTask renderTask;
    private YapTask markersTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
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
        renderTask = YapSched.globalTimer(this, () -> new MapRenderTask(this, config, renderer).run(),
                100L, periodTicks);
        YapSched.globalLater(this, () -> new MapRenderTask(this, config, renderer).run(), 40L);
        long markerTicks = Math.max(40L, config.markersPollSeconds() * 20L);
        markersTask = YapSched.globalTimer(this, this::writeMarkersFile, 60L, markerTicks);
        YapSched.globalLater(this, this::writeMarkersFile, 40L);

        var cmd = getCommand("yapmap");
        if (cmd != null) {
            cmd.setExecutor(this);
        }

        getLogger().info("YaPMap ready — tiles in " + tilesRoot().toAbsolutePath());
    }

    private void startEmbeddedHttp() {
        int port = config.port();
        try {
            httpServer = new MapHttpServer(config.bindHost(), port, tilesRoot(),
                    () -> MapMarkers.toJson(config));
            httpServer.start();
            getLogger().info("Map HTTP on http://" + config.bindHost() + ":" + port + "/map/");
        } catch (IOException e) {
            if (port == 8081) {
                try {
                    httpServer = new MapHttpServer(config.bindHost(), 8082, tilesRoot(),
                            () -> MapMarkers.toJson(config));
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
        renderer = new TileRenderer(config, tilesRoot());
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

    private void extractWebAssets() throws IOException {
        Path web = webRoot();
        Files.createDirectories(web);
        copyResource("index.html", web.resolve("index.html"));
        copyResource("map.js", web.resolve("map.js"));
        String cfg = "window.YAP_MAP_CONFIG={sampleChunkRadius:" + config.sampleChunkRadius() + "};\n";
        Files.writeString(web.resolve("map-config.js"), cfg, StandardCharsets.UTF_8);
        writeMarkersFile();
    }

    private void writeMarkersFile() {
        try {
            Path web = webRoot();
            Files.createDirectories(web);
            Files.writeString(web.resolve("markers.json"), MapMarkers.toJson(config), StandardCharsets.UTF_8);
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
            new MapRenderTask(this, config, renderer).run();
            sender.sendMessage("§aMap render queued.");
            return true;
        }
        sender.sendMessage("§e/yapmap reload|render");
        return true;
    }

    @Override
    public void onDisable() {
        if (renderTask != null) {
            renderTask.cancel();
        }
        if (markersTask != null) {
            markersTask.cancel();
        }
        if (httpServer != null) {
            httpServer.stop();
        }
    }
}
