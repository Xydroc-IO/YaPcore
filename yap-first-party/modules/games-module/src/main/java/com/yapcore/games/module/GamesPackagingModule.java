package com.yapcore.games.module;

import com.yapcore.api.module.YaPModule;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/** Operator packaging for the YaPGames match kernel plugin. */
public final class GamesPackagingModule extends YaPModule {

    @Override
    public void onEnable() {
        try {
            Plugin games = Bukkit.getPluginManager().getPlugin("YaPGames");
            if (games != null && games.isEnabled()) {
                getLogger().info("YaP Games module OK — Paper plugin YaPGames is online");
            } else {
                getLogger().warning(
                        "YaP Games module loaded, but plugins/yap-games.jar (YaPGames) is missing or disabled.");
            }
        } catch (NoClassDefFoundError | ExceptionInInitializerError e) {
            getLogger().warning(
                    "Bukkit not available — drop yap-games.jar into plugins/ under game-authority=paper.");
        }
    }
}
