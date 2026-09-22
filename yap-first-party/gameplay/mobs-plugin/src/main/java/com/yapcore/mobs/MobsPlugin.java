package com.yapcore.mobs;

import com.yapcore.leveledmobs.LeveledMobsConfig;
import com.yapcore.leveledmobs.LeveledMobsPlugin;
import com.yapcore.stacker.StackerConfig;
import com.yapcore.stacker.StackerPlugin;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * GAMEPLAY opt-in mob mechanics: stacking plus distance-based levels.
 * {@code provides} keeps {@code YaPStacker} and {@code YaPLeveledMobs} lookups working.
 */
public final class MobsPlugin extends JavaPlugin {

    private StackerPlugin stacker;
    private LeveledMobsPlugin leveled;

    @Override
    public void onEnable() {
        this.stacker = new StackerPlugin(this);
        this.leveled = new LeveledMobsPlugin(this);
        stacker.enable();
        leveled.enable();
        bind("yapstacker", stacker);
        getLogger().info("YaPMobs ready — stacker + leveled mobs (opt-in via each config)");
    }

    @Override
    public void onDisable() {
        if (stacker != null) {
            stacker.disable();
        }
    }

    public StackerConfig stackerConfig() {
        return stacker == null ? null : stacker.stackerConfig();
    }

    public LeveledMobsConfig leveledConfig() {
        return leveled == null ? null : leveled.leveledConfig();
    }

    /** Hosted leveled-mobs module (for soft bridges such as YaPAdmin spawnmob). */
    public LeveledMobsPlugin leveled() {
        return leveled;
    }

    public void reloadLeveled() {
        if (leveled != null) {
            leveled.reloadLeveled();
        }
    }

    private void bind(String name, StackerPlugin executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            return;
        }
        cmd.setExecutor(executor);
        cmd.setTabCompleter(executor);
    }
}
