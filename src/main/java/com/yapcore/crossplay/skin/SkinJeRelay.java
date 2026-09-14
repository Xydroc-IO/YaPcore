package com.yapcore.crossplay.skin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Chassis {@link SkinService} → Folia Tailor {@code PresenceChannel.broadcastExternalSkin}
 * via Paper reflection (same pattern as {@link com.yapcore.crossplay.emote.EmoteJeRelay}).
 *
 * <p>Lets JE {@code yap-presence} clients draw Bedrock-ingested skins (including persona
 * skins whose packet already carries assembled {@code geometryData}).
 */
public final class SkinJeRelay {

    private static final Logger LOG = Logger.getLogger("YaPcore.SkinJe");

    private SkinJeRelay() {
    }

    public static void broadcastViaPaper(Object paperLoaderOrNull, SkinService skinService, String username) {
        if (skinService == null || username == null || username.isBlank()) {
            return;
        }
        SkinService.SkinData data = skinService.get(username);
        if (data == null || data.uuid() == null) {
            return;
        }
        BedrockCanonicalSkin canonical = data.canonical();
        if (canonical != null) {
            canonical = canonical.ensureGeometryData();
        }
        boolean slim = canonical != null ? canonical.slim() : data.slim();
        String geometry = canonical != null ? canonical.geometryData() : "";
        String url = publicSkinUrl(skinService, data);
        if (url == null || url.isBlank()) {
            return;
        }
        broadcastViaPaper(paperLoaderOrNull, data.uuid(), slim, url, geometry == null ? "" : geometry);
    }

    public static void broadcastViaPaper(
            Object paperLoaderOrNull,
            UUID playerUuid,
            boolean slim,
            String skinUrl,
            String geometryJson) {
        if (playerUuid == null || skinUrl == null || skinUrl.isBlank()) {
            return;
        }
        try {
            ClassLoader loader = paperLoaderOrNull instanceof ClassLoader cl
                    ? cl
                    : SkinJeRelay.class.getClassLoader();
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
            Method broadcast = channel.getClass().getMethod(
                    "broadcastExternalSkin",
                    UUID.class,
                    boolean.class,
                    String.class,
                    String.class);
            broadcast.invoke(channel, playerUuid, slim, skinUrl, geometryJson == null ? "" : geometryJson);
        } catch (ClassNotFoundException e) {
            LOG.fine("Paper/Bukkit not on classpath for skin JE relay");
        } catch (Exception e) {
            LOG.log(Level.FINE, "Skin JE relay failed: " + e.getMessage());
        }
    }

    static String publicSkinUrl(SkinService skinService, SkinService.SkinData data) {
        if (data == null || data.uuid() == null) {
            return null;
        }
        String base = skinService.publicSkinBaseUrl();
        if (base == null || base.isBlank()) {
            return null;
        }
        String trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        if (trimmed.endsWith("/skin") || trimmed.contains("/skin/")) {
            if (trimmed.endsWith(".png")) {
                return trimmed;
            }
            return trimmed.endsWith("/")
                    ? trimmed + data.uuid() + ".png"
                    : trimmed + "/" + data.uuid() + ".png";
        }
        return trimmed + "/skin/" + data.uuid() + ".png";
    }
}
