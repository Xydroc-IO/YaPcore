package com.yapcore.map;

import com.yapcore.sched.YapSched;
import com.yapcore.sched.YapTask;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Path;

public final class MapPlugin extends JavaPlugin implements CommandExecutor {

    private MapConfig config;
    private TileRenderer renderer;
    private MapHttpServer httpServer;
    private YapTask renderTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadMap();

        try {
            httpServer = new MapHttpServer(config.bindHost(), config.port(), tilesRoot());
            httpServer.start();
        } catch (IOException e) {
            getLogger().severe("Map HTTP server failed — disabling: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        long periodTicks = Math.max(20L, config.renderIntervalMinutes() * 60L * 20L);
        renderTask = YapSched.globalTimer(this, () -> new MapRenderTask(this, config, renderer).run(),
                100L, periodTicks);
        YapSched.globalLater(this, () -> new MapRenderTask(this, config, renderer).run(), 40L);

        var cmd = getCommand("yapmap");
        if (cmd != null) {
            cmd.setExecutor(this);
        }

        getLogger().info("YaPMap ready — http://" + config.bindHost() + ":" + config.port() + "/map/");
    }

    public void reloadMap() {
        if (config == null) {
            config = new MapConfig(this);
        }
        config.reload();
        renderer = new TileRenderer(config, tilesRoot());
    }

    public Path tilesRoot() {
        return getDataFolder().toPath().resolve("map/tiles");
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
        if (httpServer != null) {
            httpServer.stop();
        }
    }
}
