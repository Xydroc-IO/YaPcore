package com.yapcore.leveledmobs;

import com.yapcore.leveledmobs.cmd.LeveledMobsCommand;
import com.yapcore.leveledmobs.listener.MobCombatListener;
import com.yapcore.leveledmobs.listener.MobSpawnListener;
import com.yapcore.leveledmobs.service.LevelCalculator;
import com.yapcore.leveledmobs.service.MobHooks;
import com.yapcore.leveledmobs.service.MobLevelApplier;
import com.yapcore.leveledmobs.service.MobLevelStore;
import com.yapcore.mobs.YamlConfigFile;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** Distance-based mob levels hosted by {@link com.yapcore.mobs.MobsPlugin}. */
public final class LeveledMobsPlugin {

    private final JavaPlugin plugin;
    private YamlConfigFile files;
    private LeveledMobsConfig config;
    private MobLevelStore store;
    private MobHooks hooks;
    private LevelCalculator calculator;
    private MobLevelApplier applier;

    public LeveledMobsPlugin(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public JavaPlugin bukkit() {
        return plugin;
    }

    public void enable() {
        files = YamlConfigFile.open(plugin, "leveled.yml", "leveled.yml", "YaPLeveledMobs");
        config = new LeveledMobsConfig(files);
        config.reload();
        store = new MobLevelStore(plugin);
        hooks = new MobHooks(config);
        calculator = new LevelCalculator(config);
        applier = new MobLevelApplier(config, store);

        plugin.getServer().getPluginManager().registerEvents(new MobSpawnListener(this), plugin);
        plugin.getServer().getPluginManager().registerEvents(new MobCombatListener(this), plugin);

        LeveledMobsCommand commands = new LeveledMobsCommand(this);
        PluginCommand cmd = plugin.getCommand("yaplevel");
        if (cmd != null) {
            cmd.setExecutor(commands);
            cmd.setTabCompleter(commands);
        }

        plugin.getLogger().info("YaPLeveledMobs ready — strategy=" + config.strategy()
                + " levels=" + config.minLevel() + ".." + config.maxLevel()
                + " blocks/level=" + config.blocksPerLevel());
    }

    public void reloadLeveled() {
        if (files != null) {
            files.reloadConfig();
        }
        config.reload();
        hooks = new MobHooks(config);
        calculator = new LevelCalculator(config);
        applier = new MobLevelApplier(config, store);
    }

    public LeveledMobsConfig leveledConfig() {
        return config;
    }

    public MobLevelStore store() {
        return store;
    }

    public MobHooks hooks() {
        return hooks;
    }

    public LevelCalculator calculator() {
        return calculator;
    }

    public MobLevelApplier applier() {
        return applier;
    }
}
