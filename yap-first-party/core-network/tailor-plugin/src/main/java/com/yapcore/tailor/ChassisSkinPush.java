package com.yapcore.tailor;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Push a Tailor-applied skin into the YaPcore chassis {@code SkinService}
 * (same-JVM reflection) or via HTTP {@code POST {base}/apply} when Folia is
 * a separate process from the chassis.
 */
public final class ChassisSkinPush {

    private ChassisSkinPush() {
    }

    /**
     * Best-effort push. Returns the Bedrock-canonical JSON to persist when the
     * chassis accepted the skin; empty if both reflection and HTTP failed.
     */
    public static Optional<String> push(
            JavaPlugin plugin,
            TailorConfig config,
            Player player,
            ActiveSkin activeSkin,
            byte[] skinPng,
            byte[] capePng) {
        if (player == null) {
            return Optional.empty();
        }
        return push(plugin, config, player.getName(), player.getUniqueId(), activeSkin, skinPng, capePng);
    }

    public static Optional<String> push(
            JavaPlugin plugin,
            TailorConfig config,
            String username,
            UUID uuid,
            ActiveSkin activeSkin,
            byte[] skinPng,
            byte[] capePng) {
        if (plugin == null || username == null || username.isBlank() || uuid == null || activeSkin == null) {
            return Optional.empty();
        }
        Logger log = plugin.getLogger();
        boolean slim = activeSkin.model() == SkinModel.SLIM;

        writeSharedSkinsDirBestEffort(plugin, uuid, skinPng, capePng, log);

        Optional<String> viaReflection = pushViaReflection(username, uuid, slim, skinPng, capePng, activeSkin, log);
        if (viaReflection.isPresent()) {
            return viaReflection;
        }
        return pushViaHttp(config, username, uuid, slim, skinPng, capePng, activeSkin, log);
    }

    /**
     * @return present with canonical JSON on success; empty if chassis not available or push failed
     */
    private static Optional<String> pushViaReflection(
            String username,
            UUID uuid,
            boolean slim,
            byte[] skinPng,
            byte[] capePng,
            ActiveSkin activeSkin,
            Logger log) {
        try {
            Class<?> holderClass = Class.forName("com.yapcore.crossplay.bedrock.BedrockUiGatewayHolder");
            Object gateway = holderClass.getMethod("gateway").invoke(null);
            if (gateway == null) {
                return Optional.empty();
            }
            Object skinService = gateway.getClass().getMethod("skinService").invoke(gateway);
            if (skinService == null) {
                return Optional.empty();
            }

            // Prefer writing into chassis skins dir when available
            try {
                Object dir = skinService.getClass().getMethod("skinsDir").invoke(skinService);
                if (dir instanceof Path skinsDir && skinPng != null && skinPng.length > 0) {
                    Files.createDirectories(skinsDir);
                    Files.write(skinsDir.resolve(uuid + ".png"), skinPng);
                }
            } catch (ReflectiveOperationException | java.io.IOException ignored) {
                // optional
            }

            // putCanonical(username, BedrockCanonicalSkin) when present
            try {
                Class<?> canonicalCl = Class.forName("com.yapcore.crossplay.skin.BedrockCanonicalSkin");
                Object canonical;
                String json = activeSkin.bedrockCanonicalJson();
                if (json != null && !json.isBlank()) {
                    canonical = canonicalCl.getMethod("fromJson", String.class).invoke(null, json);
                } else {
                    Method classicPng = canonicalCl.getMethod(
                            "classicPng", UUID.class, byte[].class, byte[].class, boolean.class);
                    canonical = classicPng.invoke(null, uuid, skinPng, capePng, slim);
                }
                skinService.getClass()
                        .getMethod("putCanonical", String.class, canonicalCl)
                        .invoke(skinService, username, canonical);
                log.fine("ChassisSkinPush putCanonical for " + username);

                String persisted = readCanonicalJson(skinService, username, canonicalCl);
                if (persisted == null || persisted.isBlank()) {
                    persisted = buildCompactCanonicalJson(uuid, slim, skinPng, capePng, activeSkin);
                }
                return Optional.of(persisted);
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                // older chassis — fall through to putJavaSkin
            }

            skinService.getClass()
                    .getMethod("putJavaSkin", String.class, UUID.class, byte[].class, byte[].class, boolean.class)
                    .invoke(skinService, username, uuid, skinPng, capePng, slim);
            log.fine("ChassisSkinPush putJavaSkin for " + username);
            return Optional.of(buildCompactCanonicalJson(uuid, slim, skinPng, capePng, activeSkin));
        } catch (ClassNotFoundException e) {
            return Optional.empty();
        } catch (Exception e) {
            log.log(Level.FINE, "ChassisSkinPush reflection failed: " + e.getMessage(), e);
            return Optional.empty();
        }
    }

    private static String readCanonicalJson(Object skinService, String username, Class<?> canonicalCl) {
        try {
            Object got = skinService.getClass().getMethod("getCanonical", String.class).invoke(skinService, username);
            if (got == null) {
                return null;
            }
            Object json = canonicalCl.getMethod("toJson").invoke(got);
            return json == null ? null : json.toString();
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Optional<String> pushViaHttp(
            TailorConfig config,
            String username,
            UUID uuid,
            boolean slim,
            byte[] skinPng,
            byte[] capePng,
            ActiveSkin activeSkin,
            Logger log) {
        String base = resolveSkinApplyBase(config);
        String applyUrl = base.endsWith("/") ? base + "apply" : base + "/apply";
        try {
            String body = buildApplyJson(username, uuid, slim, skinPng, capePng, activeSkin);
            URL url = URI.create(applyUrl).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(4_000);
            conn.setReadTimeout(8_000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("User-Agent", "YaPTailor/1.0");
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(payload.length);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(payload);
            }
            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                String responseBody = "";
                try (InputStream in = conn.getInputStream()) {
                    if (in != null) {
                        responseBody = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    }
                }
                conn.disconnect();
                log.fine("ChassisSkinPush HTTP apply ok for " + username + " → " + applyUrl);
                String fromResponse = extractJsonStringField(responseBody, "bedrockCanonicalJson");
                if (fromResponse != null && !fromResponse.isBlank()) {
                    return Optional.of(fromResponse);
                }
                return Optional.of(buildCompactCanonicalJson(uuid, slim, skinPng, capePng, activeSkin));
            }
            log.warning("ChassisSkinPush HTTP apply HTTP " + code + " for " + applyUrl);
            conn.disconnect();
            return Optional.empty();
        } catch (Exception e) {
            log.log(Level.FINE, "ChassisSkinPush HTTP apply failed (" + applyUrl + "): " + e.getMessage(), e);
            return Optional.empty();
        }
    }

    static String resolveSkinApplyBase(TailorConfig config) {
        String configured = config == null ? null : config.skinHostPublicBaseUrl();
        if (configured != null && !configured.isBlank()) {
            String base = configured.trim();
            while (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            // Configured texture CDN may be host root; chassis apply lives under /skin
            if (base.endsWith("/skin")) {
                return base;
            }
            return base + "/skin";
        }
        int port = resolvePackHttpPort();
        return "http://127.0.0.1:" + port + "/skin";
    }

    static int resolvePackHttpPort() {
        String[] keys = {
                "YAP_RESOURCE_PACK_HTTP_PORT",
                "RESOURCE_PACK_HTTP_PORT",
                "resource-pack-http-port"
        };
        for (String key : keys) {
            String v = System.getenv(key);
            if (v == null || v.isBlank()) {
                v = System.getProperty(key);
            }
            if (v != null && !v.isBlank()) {
                try {
                    int p = Integer.parseInt(v.trim());
                    if (p > 0 && p < 65536) {
                        return p;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return 8081;
    }

    static String buildApplyJson(
            String username,
            UUID uuid,
            boolean slim,
            byte[] skinPng,
            byte[] capePng,
            ActiveSkin activeSkin) {
        StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        sb.append("\"username\":\"").append(escapeJson(username)).append('"');
        sb.append(",\"uuid\":\"").append(uuid).append('"');
        sb.append(",\"slim\":").append(slim);
        sb.append(",\"skinPngBase64\":\"").append(b64(skinPng)).append('"');
        sb.append(",\"capePngBase64\":\"").append(b64(capePng)).append('"');
        String canonical = activeSkin == null ? null : activeSkin.bedrockCanonicalJson();
        if (canonical != null && !canonical.isBlank()) {
            sb.append(",\"bedrockCanonicalJson\":");
            appendRawOrQuotedJson(sb, canonical);
        } else {
            sb.append(",\"bedrockCanonicalJson\":null");
        }
        sb.append('}');
        return sb.toString();
    }

    /**
     * Compact classic-field JSON mirroring {@code BedrockCanonicalSkin} essentials so the
     * Tailor DB is not left with a null canonical after a successful push.
     */
    static String buildCompactCanonicalJson(
            UUID uuid,
            boolean slim,
            byte[] skinPng,
            byte[] capePng,
            ActiveSkin activeSkin) {
        String skinId = activeSkin != null && activeSkin.skinId() != null && !activeSkin.skinId().isBlank()
                ? activeSkin.skinId()
                : (slim ? "Standard_CustomSlim" : "Standard_Custom");
        String geometryName = activeSkin != null && activeSkin.geometryName() != null && !activeSkin.geometryName().isBlank()
                ? activeSkin.geometryName()
                : (slim ? "geometry.humanoid.customSlim" : "geometry.humanoid.custom");
        StringBuilder sb = new StringBuilder(128);
        sb.append('{');
        sb.append("\"uuid\":\"").append(uuid).append('"');
        sb.append(",\"skinId\":\"").append(escapeJson(skinId)).append('"');
        sb.append(",\"slim\":").append(slim);
        sb.append(",\"geometryName\":\"").append(escapeJson(geometryName)).append('"');
        sb.append(",\"skinPngBase64\":\"").append(b64(skinPng)).append('"');
        sb.append(",\"capePngBase64\":\"").append(b64(capePng)).append('"');
        sb.append(",\"geometryData\":\"\"");
        sb.append('}');
        return sb.toString();
    }

    /** Extract a JSON string field; supports nested object embedded as raw JSON. */
    static String extractJsonStringField(String json, String field) {
        if (json == null || json.isBlank() || field == null) {
            return null;
        }
        String key = "\"" + field + "\"";
        int idx = json.indexOf(key);
        if (idx < 0) {
            return null;
        }
        int colon = json.indexOf(':', idx + key.length());
        if (colon < 0) {
            return null;
        }
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        if (i >= json.length() || json.regionMatches(i, "null", 0, 4)) {
            return null;
        }
        if (json.charAt(i) == '{') {
            int depth = 0;
            int start = i;
            for (; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return json.substring(start, i + 1);
                    }
                }
            }
            return null;
        }
        if (json.charAt(i) != '"') {
            return null;
        }
        int start = i + 1;
        StringBuilder out = new StringBuilder();
        for (int j = start; j < json.length(); j++) {
            char c = json.charAt(j);
            if (c == '\\' && j + 1 < json.length()) {
                out.append(json.charAt(j + 1));
                j++;
                continue;
            }
            if (c == '"') {
                return out.toString();
            }
            out.append(c);
        }
        return null;
    }

    /** If value looks like JSON object/array, embed raw; otherwise quote as string. */
    private static void appendRawOrQuotedJson(StringBuilder sb, String value) {
        String t = value.trim();
        if (t.startsWith("{") || t.startsWith("[")) {
            sb.append(t);
        } else {
            sb.append('"').append(escapeJson(t)).append('"');
        }
    }

    private static String b64(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        return Base64.getEncoder().encodeToString(data);
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Best-effort write into server-root {@code skins/} (sibling of {@code packs/}),
     * matching chassis {@code ResourcePackManager#skinsDir()}.
     */
    public static void writeSharedSkinPng(JavaPlugin plugin, UUID uuid, byte[] skinPng) {
        writeSharedSkinsDirBestEffort(plugin, uuid, skinPng, null, plugin.getLogger());
    }

    public static void writeSharedCapePng(JavaPlugin plugin, UUID uuid, byte[] capePng) {
        writeSharedSkinsDirBestEffort(plugin, uuid, null, capePng, plugin.getLogger());
    }

    public static void writeSharedWardrobeSkinPng(JavaPlugin plugin, UUID playerUuid, long slotId, byte[] skinPng)
            throws IOException {
        Path skins = requireSharedSkinsDir(plugin);
        SharedWardrobeAssets.writeSkin(skins, playerUuid, slotId, skinPng);
        if (plugin != null) {
            plugin.getLogger().fine("ChassisSkinPush wrote wardrobe/" + playerUuid + "/" + slotId + ".png");
        }
    }

    public static void writeSharedWardrobeCapePng(JavaPlugin plugin, UUID playerUuid, long slotId, byte[] capePng)
            throws IOException {
        Path skins = requireSharedSkinsDir(plugin);
        SharedWardrobeAssets.writeCape(skins, playerUuid, slotId, capePng);
        if (plugin != null && capePng != null && capePng.length > 0) {
            plugin.getLogger().fine("ChassisSkinPush wrote wardrobe/" + playerUuid + "/" + slotId + "_cape.png");
        }
    }

    public static void deleteSharedWardrobePng(JavaPlugin plugin, UUID playerUuid, long slotId) throws IOException {
        Path skins = requireSharedSkinsDir(plugin);
        SharedWardrobeAssets.delete(skins, playerUuid, slotId);
    }

    /** Visible for tests and callers that need to probe before write. */
    public static Path resolveSharedSkinsDirOrNull(JavaPlugin plugin) {
        return resolveSharedSkinsDir(plugin);
    }

    /** Resolves skins root or throws — used by wardrobe mirror writes. */
    static Path requireSharedSkinsDir(JavaPlugin plugin) throws IOException {
        Path skins = resolveSharedSkinsDir(plugin);
        if (skins == null) {
            throw new IOException(
                    "Cannot resolve shared skins/ directory — set -Dyapcore.home or run under a YaP server root "
                            + "with skins/ or packs/");
        }
        Files.createDirectories(skins);
        return skins;
    }

    private static Path resolveSharedSkinsDir(JavaPlugin plugin) {
        String home = System.getProperty("yapcore.home");
        if (home != null && !home.isBlank()) {
            return Path.of(home).resolve("skins");
        }
        if (plugin == null) {
            return null;
        }
        Path data = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        Path plugins = data.getParent();
        if (plugins == null) {
            return null;
        }
        Path root = plugins.getParent();
        if (root == null) {
            return null;
        }
        Path skins = root.resolve("skins");
        Path packs = root.resolve("packs");
        if (!Files.isDirectory(packs) && !Files.isDirectory(skins)) {
            return null;
        }
        return skins;
    }

    private static void writeSharedSkinsDirBestEffort(
            JavaPlugin plugin, UUID uuid, byte[] skinPng, byte[] capePng, Logger log) {
        if (uuid == null || plugin == null) {
            return;
        }
        if ((skinPng == null || skinPng.length == 0) && (capePng == null || capePng.length == 0)) {
            return;
        }
        try {
            Path skins = resolveSharedSkinsDir(plugin);
            if (skins == null) {
                return;
            }
            Files.createDirectories(skins);
            if (skinPng != null && skinPng.length > 0) {
                Files.write(skins.resolve(uuid + ".png"), skinPng);
                log.fine("ChassisSkinPush wrote shared skins/" + uuid + ".png");
            }
            if (capePng != null && capePng.length > 0) {
                Files.write(skins.resolve(uuid + "_cape.png"), capePng);
                log.fine("ChassisSkinPush wrote shared skins/" + uuid + "_cape.png");
            }
        } catch (Exception e) {
            log.log(Level.FINE, "ChassisSkinPush shared skins write skipped: " + e.getMessage());
        }
    }
}
