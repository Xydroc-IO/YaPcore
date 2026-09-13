package com.yapcore.tailor;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JE → chassis emote play: same-JVM reflection on {@code BedrockGameplayBridge#playEmote}
 * or HTTP {@code POST /emote/play}.
 */
public final class ChassisEmotePush {

    private ChassisEmotePush() {
    }

    public static boolean push(JavaPlugin plugin, TailorConfig config, String username, UUID uuid, String emoteId) {
        if (plugin == null || username == null || uuid == null || emoteId == null || emoteId.isBlank()) {
            return false;
        }
        Logger log = plugin.getLogger();
        if (pushViaReflection(username, uuid, emoteId, log)) {
            return true;
        }
        return pushViaHttp(config, username, uuid, emoteId, log);
    }

    private static boolean pushViaReflection(String username, UUID uuid, String emoteId, Logger log) {
        try {
            Class<?> gatewayHolder = Class.forName("com.yapcore.protocol.DualStackGateway");
            // Prefer YaPcoreServer.getGateway() style discovery via classloader scan of known holder
            Object bridge = findBridge();
            if (bridge == null) {
                return false;
            }
            Method play = bridge.getClass().getMethod(
                    "playEmote", UUID.class, String.class, String.class, String.class);
            Object result = play.invoke(bridge, uuid, username, emoteId, "JE");
            if (result instanceof Optional<?> opt) {
                return opt.isPresent();
            }
            return result != null;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (Exception e) {
            log.log(Level.FINE, "Chassis emote reflection failed: " + e.getMessage());
            return false;
        }
    }

    private static Object findBridge() {
        try {
            Class<?> holder = Class.forName("com.yapcore.crossplay.bedrock.BedrockUiGatewayHolder");
            Method get = holder.getMethod("gateway");
            Object gateway = get.invoke(null);
            if (gateway == null) {
                return null;
            }
            Method bridge = gateway.getClass().getMethod("bedrockBridge");
            return bridge.invoke(gateway);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean pushViaHttp(
            TailorConfig config, String username, UUID uuid, String emoteId, Logger log) {
        String base = config.skinHostPublicBaseUrl();
        if (base == null || base.isBlank()) {
            base = "http://127.0.0.1:8081";
        }
        String url = base.endsWith("/") ? base + "emote/play" : base + "/emote/play";
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(3_000);
            conn.setReadTimeout(5_000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            String json = "{\"username\":\"" + escape(username)
                    + "\",\"uuid\":\"" + uuid
                    + "\",\"emoteId\":\"" + escape(emoteId) + "\"}";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(bytes);
            }
            int code = conn.getResponseCode();
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (in != null) {
                in.readAllBytes();
                in.close();
            }
            conn.disconnect();
            return code >= 200 && code < 300;
        } catch (Exception e) {
            log.log(Level.FINE, "Chassis emote HTTP failed: " + e.getMessage());
            return false;
        }
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
