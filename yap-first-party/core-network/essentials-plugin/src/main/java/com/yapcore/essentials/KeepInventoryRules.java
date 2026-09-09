package com.yapcore.essentials;

import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

/** Applies {@code death.keep-inventory} to vanilla KEEP_INVENTORY gamerule. */
public final class KeepInventoryRules {

    private KeepInventoryRules() {
    }

    public static void sync(JavaPlugin plugin, boolean keep) {
        YapSched.global(plugin, () -> {
            for (World world : Bukkit.getWorlds()) {
                try {
                    world.setGameRule(GameRules.KEEP_INVENTORY, keep);
                } catch (Throwable t) {
                    plugin.getLogger().warning("Could not set keepInventory in "
                            + world.getName() + ": " + t.getMessage());
                }
            }
        });
    }
}
