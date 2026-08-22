package com.yapcore.games.module;

import com.yapcore.api.module.YaPModule;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Packaging module for FFA — runtime engine is {@code YaPGames} with {@code modes/ffa.yml}.
 */
public final class FfaPackagingModule extends YaPModule {

    @Override
    public void onEnable() {
        try {
            Plugin games = Bukkit.getPluginManager().getPlugin("YaPGames");
            if (games != null && games.isEnabled()) {
                getLogger().info("YaP Games FFA module OK — tune plugins/YaPGames/modes/ffa.yml");
            } else {
                getLogger().warning(
                        "YaP Games FFA module loaded, but yap-games.jar (YaPGames) is missing or disabled.");
            }
        } catch (NoClassDefFoundError | ExceptionInInitializerError e) {
            getLogger().warning(
                    "Bukkit not available — install yap-games.jar under game-authority=paper for FFA.");
        }
    }
}
