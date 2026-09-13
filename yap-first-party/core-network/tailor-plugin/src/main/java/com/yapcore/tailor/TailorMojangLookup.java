package com.yapcore.tailor;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/** Mojang session/profile texture lookup and base64 texture property parsing. */
final class TailorMojangLookup {

    private TailorMojangLookup() {
    }

    record MojangTextures(String skinUrl, String capeUrl, SkinModel model, String textureValue) {
    }

    static MojangTextures lookupByName(String name, SkinModel defaultModel) throws TailorException {
        try {
            URL uuidUrl = URI.create("https://api.mojang.com/users/profiles/minecraft/"
                    + java.net.URLEncoder.encode(name, StandardCharsets.UTF_8)).toURL();
            String uuidJson = httpGet(uuidUrl);
            if (uuidJson == null || uuidJson.isBlank() || !uuidJson.contains("\"id\"")) {
                throw new TailorException("Player not found: " + name);
            }
            String id = jsonStringField(uuidJson, "id");
            if (id == null) {
                throw new TailorException("Player not found: " + name);
            }
            URL sessionUrl = URI.create(
                    "https://sessionserver.mojang.com/session/minecraft/profile/" + id + "?unsigned=false").toURL();
            String profileJson = httpGet(sessionUrl);
            if (profileJson == null || profileJson.isBlank()) {
                throw new TailorException("No Mojang profile for " + name);
            }
            String value = extractTexturesValue(profileJson);
            if (value == null) {
                throw new TailorException("No textures for " + name);
            }
            return new MojangTextures(
                    extractSkinUrl(value).orElse(null),
                    extractCapeUrl(value).orElse(null),
                    extractModel(value).orElse(defaultModel),
                    value);
        } catch (TailorException e) {
            throw e;
        } catch (Exception e) {
            throw new TailorException("Mojang lookup failed: " + e.getMessage(), e);
        }
    }

    static Optional<String> extractSkinUrl(String textureValueBase64) {
        try {
            String json = new String(Base64.getDecoder().decode(textureValueBase64), StandardCharsets.UTF_8);
            int skinIdx = json.indexOf("\"SKIN\"");
            if (skinIdx < 0) {
                return Optional.empty();
            }
            int urlIdx = json.indexOf("\"url\"", skinIdx);
            if (urlIdx < 0) {
                return Optional.empty();
            }
            int start = json.indexOf('"', urlIdx + 5);
            int end = json.indexOf('"', start + 1);
            if (start < 0 || end < 0) {
                return Optional.empty();
            }
            // find value after "url":
            int colon = json.indexOf(':', urlIdx);
            start = json.indexOf('"', colon + 1);
            end = json.indexOf('"', start + 1);
            if (start < 0 || end < 0) {
                return Optional.empty();
            }
            return Optional.of(json.substring(start + 1, end));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    static Optional<String> extractCapeUrl(String textureValueBase64) {
        try {
            String json = new String(Base64.getDecoder().decode(textureValueBase64), StandardCharsets.UTF_8);
            int capeIdx = json.indexOf("\"CAPE\"");
            if (capeIdx < 0) {
                return Optional.empty();
            }
            int urlIdx = json.indexOf("\"url\"", capeIdx);
            if (urlIdx < 0) {
                return Optional.empty();
            }
            int colon = json.indexOf(':', urlIdx);
            int start = json.indexOf('"', colon + 1);
            int end = json.indexOf('"', start + 1);
            if (start < 0 || end < 0) {
                return Optional.empty();
            }
            return Optional.of(json.substring(start + 1, end));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    static Optional<SkinModel> extractModel(String textureValueBase64) {
        try {
            String json = new String(Base64.getDecoder().decode(textureValueBase64), StandardCharsets.UTF_8);
            if (json.toLowerCase().contains("\"model\":\"slim\"")) {
                return Optional.of(SkinModel.SLIM);
            }
            return Optional.of(SkinModel.WIDE);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String extractTexturesValue(String profileJson) {
        int nameIdx = profileJson.indexOf("\"textures\"");
        if (nameIdx < 0) {
            return null;
        }
        int valueIdx = profileJson.indexOf("\"value\"", nameIdx);
        if (valueIdx < 0) {
            // sometimes value comes before name in object — search properties array
            valueIdx = profileJson.indexOf("\"value\"");
        }
        if (valueIdx < 0) {
            return null;
        }
        int colon = profileJson.indexOf(':', valueIdx);
        int start = profileJson.indexOf('"', colon + 1);
        int end = profileJson.indexOf('"', start + 1);
        if (start < 0 || end < 0) {
            return null;
        }
        return profileJson.substring(start + 1, end);
    }

    private static String jsonStringField(String json, String field) {
        String key = "\"" + field + "\"";
        int idx = json.indexOf(key);
        if (idx < 0) {
            return null;
        }
        int colon = json.indexOf(':', idx + key.length());
        int start = json.indexOf('"', colon + 1);
        int end = json.indexOf('"', start + 1);
        if (start < 0 || end < 0) {
            return null;
        }
        return json.substring(start + 1, end);
    }

    private static String httpGet(URL url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(8_000);
        conn.setReadTimeout(10_000);
        conn.setRequestProperty("User-Agent", "YaPTailor/1.0");
        conn.setRequestMethod("GET");
        int code = conn.getResponseCode();
        if (code == 204 || code == 404) {
            return null;
        }
        if (code < 200 || code >= 300) {
            throw new TailorException("HTTP " + code + " from " + url.getHost());
        }
        try (InputStream in = conn.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            conn.disconnect();
        }
    }
}
