package com.yapcore.worldedit;

import com.sk89q.worldedit.WorldEdit;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Loads as plugin name {@code WorldEdit} so soft-deps find us; delegates to YaPWorld.
 */
public final class WorldEditShimPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        if (Bukkit.getPluginManager().getPlugin("YaPWorld") == null) {
            getLogger().severe("YaPWorld is required. Disabling WorldEdit shim.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        WorldEdit.bind(WorldEdit.getInstance());
        getLogger().info("WorldEdit API shim active (backed by YaPWorld / Folia-safe).");
        getLogger().info("ClipboardFormat.load delegates to YaPWorld SchematicCatalog — never empty stub.");
        getLogger().info("Do not install stock WorldEdit or FAWE jars alongside this shim.");
    }
}
