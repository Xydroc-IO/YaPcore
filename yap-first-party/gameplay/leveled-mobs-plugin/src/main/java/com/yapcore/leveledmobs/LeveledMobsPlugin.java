package com.yapcore.leveledmobs;

import com.yapcore.leveledmobs.cmd.LeveledMobsCommand;
import com.yapcore.leveledmobs.listener.MobCombatListener;
import com.yapcore.leveledmobs.listener.MobSpawnListener;
import com.yapcore.leveledmobs.service.LevelCalculator;
import com.yapcore.leveledmobs.service.MobHooks;
import com.yapcore.leveledmobs.service.MobLevelApplier;
import com.yapcore.leveledmobs.service.MobLevelStore;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class LeveledMobsPlugin extends JavaPlugin {

    private LeveledMobsConfig config;
    private MobLevelStore store;
    private MobHooks hooks;
    private LevelCalculator calculator;
    private MobLevelApplier applier;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = new LeveledMobsConfig(this);
        config.reload();
        store = new MobLevelStore(this);
        hooks = new MobHooks(config);
        calculator = new LevelCalculator(config);
        applier = new MobLevelApplier(config, store);

        getServer().getPluginManager().registerEvents(new MobSpawnListener(this), this);
        getServer().getPluginManager().registerEvents(new MobCombatListener(this), this);

        LeveledMobsCommand commands = new LeveledMobsCommand(this);
        PluginCommand cmd = getCommand("yaplevel");
        if (cmd != null) {
            cmd.setExecutor(commands);
            cmd.setTabCompleter(commands);
        }

        getLogger().info("YaPLeveledMobs ready — strategy=" + config.strategy()
                + " levels=" + config.minLevel() + ".." + config.maxLevel()
                + " blocks/level=" + config.blocksPerLevel());
    }

    public void reloadLeveled() {
        reloadConfig();
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
