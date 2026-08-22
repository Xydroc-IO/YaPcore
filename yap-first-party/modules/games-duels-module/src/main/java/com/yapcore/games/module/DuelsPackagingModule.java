package com.yapcore.games.module;

import com.yapcore.api.module.YaPModule;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/** Packaging module for Duels — tune {@code plugins/YaPGames/modes/duels.yml}. */
public final class DuelsPackagingModule extends YaPModule {

    @Override
    public void onEnable() {
        try {
            Plugin games = Bukkit.getPluginManager().getPlugin("YaPGames");
            if (games != null && games.isEnabled()) {
                getLogger().info("YaP Games Duels module OK — tune plugins/YaPGames/modes/duels.yml");
            } else {
                getLogger().warning(
                        "YaP Games Duels module loaded, but yap-games.jar (YaPGames) is missing or disabled.");
            }
        } catch (NoClassDefFoundError | ExceptionInInitializerError e) {
            getLogger().warning(
                    "Bukkit not available — install yap-games.jar under game-authority=paper for Duels.");
        }
    }
}
