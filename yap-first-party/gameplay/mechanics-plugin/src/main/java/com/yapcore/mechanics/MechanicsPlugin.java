package com.yapcore.mechanics;

import com.yapcore.mechanics.cmd.YMechanicsCommand;
import com.yapcore.mechanics.farming.FarmingLoader;
import com.yapcore.mechanics.listener.BlockBreakMechanicsListener;
import com.yapcore.mechanics.listener.FarmingListener;
import com.yapcore.mechanics.listener.PhysicsListener;
import com.yapcore.mechanics.listener.ResourceNodeListener;
import com.yapcore.mechanics.listener.WaterWavesListener;
import com.yapcore.mechanics.node.ResourceNodeLoader;
import com.yapcore.mechanics.physics.PhysicsLoader;
import com.yapcore.mechanics.service.MechanicsServiceImpl;
import com.yapcore.mechanics.stamina.StaminaTracker;
import com.yapcore.mechanics.tool.ToolRuleLoader;
import com.yapcore.mechanics.water.WaterWaves;
import com.yapcore.sched.YapSched;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public final class MechanicsPlugin extends JavaPlugin {

    private MechanicsConfig config;
    private ToolRuleLoader toolLoader;
    private StaminaTracker staminaTracker;
    private ResourceNodeLoader nodeLoader;
    private FarmingLoader farmingLoader;
    private PhysicsLoader physicsLoader;
    private MechanicsServiceImpl mechanicsService;
    private WaterWaves waterWaves;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadMechanics();

        if (!config.enabled()) {
            getLogger().info("YaPMechanics disabled via config.");
            return;
        }

        getServer().getPluginManager().registerEvents(new BlockBreakMechanicsListener(mechanicsService), this);
        getServer().getPluginManager().registerEvents(new ResourceNodeListener(this, mechanicsService), this);
        getServer().getPluginManager().registerEvents(new FarmingListener(this, mechanicsService, farmingLoader), this);
        getServer().getPluginManager().registerEvents(new PhysicsListener(mechanicsService), this);
        waterWaves = new WaterWaves(this, config);
        getServer().getPluginManager().registerEvents(new WaterWavesListener(waterWaves), this);

        PluginCommand cmd = getCommand("ymechanics");
        if (cmd != null) {
            cmd.setExecutor(new YMechanicsCommand(this, mechanicsService));
        }

        getServer().getServicesManager().register(MechanicsService.class, mechanicsService, this, ServicePriority.Normal);

        YapSched.globalTimer(this, () -> {
            staminaTracker.tickRegenAll(getServer().getOnlinePlayers());
            staminaTracker.tickSprintDrain(getServer().getOnlinePlayers());
        }, 20L, 20L);

        // Wave bob ~10 Hz — region-safe via player scheduler inside WaterWaves
        YapSched.globalTimer(this, () -> waterWaves.tick(getServer().getOnlinePlayers()), 10L, 2L);

        getLogger().info("YaPMechanics ready — tools=" + toolLoader.ruleCount()
                + " nodes=" + nodeLoader.nodes().size()
                + " water-waves=" + config.waterWavesEnabled());
    }

    @Override
    public void onDisable() {
        var sm = getServer().getServicesManager();
        if (mechanicsService != null) {
            sm.unregister(MechanicsService.class, mechanicsService);
        }
    }

    public void reloadMechanics() {
        if (config == null) {
            config = new MechanicsConfig(this);
        }
        config.reload();
        extractDefaults();

        toolLoader = new ToolRuleLoader();
        toolLoader.load(config.toolsPath());
        staminaTracker = new StaminaTracker(config);
        nodeLoader = new ResourceNodeLoader();
        nodeLoader.load(config.nodesPath());
        farmingLoader = new FarmingLoader();
        farmingLoader.load(config.farmingPath());
        physicsLoader = new PhysicsLoader();
        physicsLoader.load(config.physicsPath());
        mechanicsService = new MechanicsServiceImpl(config, toolLoader, staminaTracker, nodeLoader, physicsLoader);

        var sm = getServer().getServicesManager();
        if (getServer().getPluginManager().isPluginEnabled(this) && mechanicsService != null) {
            sm.unregister(MechanicsService.class, mechanicsService);
            sm.register(MechanicsService.class, mechanicsService, this, ServicePriority.Normal);
        }
    }

    public MechanicsServiceImpl mechanicsService() {
        return mechanicsService;
    }

    private void extractDefaults() {
        try {
            Files.createDirectories(getDataFolder().toPath());
            for (String file : new String[]{"tools.yml", "nodes.yml", "farming.yml", "physics.yml"}) {
                copyResourceIfMissing(file);
            }
        } catch (IOException e) {
            getLogger().warning("Could not extract defaults: " + e.getMessage());
        }
    }

    private void copyResourceIfMissing(String name) throws IOException {
        var target = getDataFolder().toPath().resolve(name);
        if (Files.exists(target)) {
            return;
        }
        try (InputStream in = getResource(name)) {
            if (in == null) {
                return;
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
