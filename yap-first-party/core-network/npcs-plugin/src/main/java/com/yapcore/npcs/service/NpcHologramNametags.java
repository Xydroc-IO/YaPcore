package com.yapcore.npcs.service;

import com.yapcore.npcs.NpcsConfig;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Entity;

/** Soft-depend YaPHolo: packet nametags instead of vanilla customName. */
public final class NpcHologramNametags {

    private NpcHologramNametags() {
    }

    public static boolean apply(JavaPlugin npcs, NpcsConfig config, String npcId, String displayName,
                                Entity entity) {
        if (!config.hologramNametags() || entity == null || npcId == null || npcId.isBlank()) {
            return false;
        }
        Plugin holo = Bukkit.getPluginManager().getPlugin("YaPHolo");
        if (holo == null || !holo.isEnabled()) {
            return false;
        }
        try {
            return NpcHologramNametagApply.sync(config, npcId, displayName, entity);
        } catch (NoClassDefFoundError | Exception e) {
            npcs.getLogger().fine("YaPHolo nametag skipped for " + npcId + ": " + e.getMessage());
            return false;
        }
    }

    public static void remove(JavaPlugin npcs, String npcId) {
        Plugin holo = Bukkit.getPluginManager().getPlugin("YaPHolo");
        if (holo == null || !holo.isEnabled() || npcId == null) {
            return;
        }
        try {
            NpcHologramNametagApply.delete(npcId);
        } catch (NoClassDefFoundError | Exception e) {
            npcs.getLogger().fine("YaPHolo nametag remove skipped: " + e.getMessage());
        }
    }
}
