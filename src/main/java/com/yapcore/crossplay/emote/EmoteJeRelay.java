package com.yapcore.crossplay.emote;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Chassis → Folia Tailor {@code PresenceChannel.broadcastEmote} via Paper reflection
 * (same pattern as Bedrock→Paper world sync).
 */
public final class EmoteJeRelay {

    private static final Logger LOG = Logger.getLogger("YaPcore.EmoteJe");

    private EmoteJeRelay() {
    }

    public static void broadcastViaPaper(Object paperLoaderOrNull, UUID playerUuid, String emoteId) {
        if (playerUuid == null || emoteId == null || emoteId.isBlank()) {
            return;
        }
        try {
            ClassLoader loader = paperLoaderOrNull instanceof ClassLoader cl
                    ? cl
                    : EmoteJeRelay.class.getClassLoader();
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, loader);
            Object pluginManager = bukkit.getMethod("getPluginManager").invoke(null);
            Object plugin = pluginManager.getClass()
                    .getMethod("getPlugin", String.class)
                    .invoke(pluginManager, "YaPTailor");
            if (plugin == null) {
                return;
            }
            Method presenceChannel = plugin.getClass().getMethod("presenceChannel");
            Object channel = presenceChannel.invoke(plugin);
            if (channel == null) {
                return;
            }
            Method broadcast = channel.getClass().getMethod("broadcastEmote", UUID.class, String.class);
            broadcast.invoke(channel, playerUuid, emoteId);
        } catch (ClassNotFoundException e) {
            LOG.fine("Paper/Bukkit not on classpath for emote JE relay");
        } catch (Exception e) {
            LOG.log(Level.FINE, "Emote JE relay failed: " + e.getMessage());
        }
    }

    public static void broadcastViaPaper(Object paperLoaderOrNull, EmoteAuthorityService.PlayEvent event) {
        if (event == null) {
            return;
        }
        broadcastViaPaper(paperLoaderOrNull, event.playerUuid(), event.emoteId());
    }
}
