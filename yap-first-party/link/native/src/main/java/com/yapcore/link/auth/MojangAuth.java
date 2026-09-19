package com.yapcore.link.auth;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.yapcore.link.forwarding.ModernForwarding;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Mojang sessionserver hasJoined for online-mode + profile texture lookup for offline skins. */
public final class MojangAuth {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Auth");
    private static final Gson GSON = new Gson();
    private static final int HAS_JOINED_ATTEMPTS = 3;
    private static final Executor EXEC = Executors.newFixedThreadPool(8, r -> {
        Thread t = new Thread(r, "yap-mojang-auth");
        t.setDaemon(true);
        return t;
    });
    /** HTTP/1.1: Java HttpClient HTTP/2 + Mojang 204s hang or look like a bad session. */
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(5))
            .executor(EXEC)
            .build();

    private MojangAuth() {
    }

    public record Profile(UUID id, String name, List<ModernForwarding.Property> properties) {
    }

    public static CompletableFuture<Profile> hasJoinedAsync(String username, String serverId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return hasJoined(username, serverId);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, EXEC);
    }

    public static Profile hasJoined(String username, String serverId) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= HAS_JOINED_ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> resp = sendHasJoined(username, serverId);
                int code = resp.statusCode();
                String body = resp.body();
                if (code == 200 && body != null && !body.isBlank()) {
                    if (attempt > 1) {
                        LOG.info("Mojang hasJoined ok user=" + username + " after retry " + attempt);
                    }
                    return parseProfile(body, username);
                }
                if (retryableStatus(code) && attempt < HAS_JOINED_ATTEMPTS) {
                    LOG.warning("Mojang hasJoined retry " + attempt + "/" + HAS_JOINED_ATTEMPTS
                            + " user=" + username + " HTTP " + code);
                    sleepBackoff(attempt);
                    continue;
                }
                if (code == 204 || body == null || body.isBlank()) {
                    throw new IllegalStateException("invalid session");
                }
                throw new IllegalStateException("Mojang auth HTTP " + code);
            } catch (IllegalStateException e) {
                throw e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            } catch (Exception e) {
                last = e;
                if (attempt < HAS_JOINED_ATTEMPTS) {
                    LOG.log(Level.WARNING, "Mojang hasJoined retry " + attempt + "/" + HAS_JOINED_ATTEMPTS
                            + " user=" + username + " " + e.getMessage());
                    sleepBackoff(attempt);
                    continue;
                }
                throw e;
            }
        }
        throw last != null ? last : new IllegalStateException("invalid session");
    }

    /**
     * Best-effort skin/textures lookup for offline-mode proxies.
     * Only attaches signed {@code textures} properties (caller decides UUID rewrite).
     */
    public static List<ModernForwarding.Property> lookupTextures(String username) {
        Profile profile = lookupProfile(username);
        return profile == null || profile.properties() == null ? List.of() : profile.properties();
    }

    /**
     * Best-effort Mojang profile (UUID + signed textures) for offline-mode proxies.
     * Returns {@code null} when the username is unknown or the lookup fails.
     */
    public static Profile lookupProfile(String username) {
        try {
            UUID mojangId = lookupUuid(username);
            if (mojangId == null) {
                return null;
            }
            String url = "https://sessionserver.mojang.com/session/minecraft/profile/"
                    + mojangId.toString().replace("-", "") + "?unsigned=false";
            HttpRequest req = mojangGet(url, Duration.ofSeconds(8));
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200 || resp.body() == null || resp.body().isBlank()) {
                return null;
            }
            return parseProfile(resp.body(), username);
        } catch (Exception e) {
            return null;
        }
    }

    public static UUID lookupUuid(String username) {
        try {
            String url = "https://api.minecraftservices.com/users/profiles/minecraft/"
                    + URLEncoder.encode(username, StandardCharsets.UTF_8);
            HttpRequest req = mojangGet(url, Duration.ofSeconds(8));
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200 || resp.body() == null || resp.body().isBlank()) {
                // Fallback legacy API
                url = "https://api.mojang.com/users/profiles/minecraft/"
                        + URLEncoder.encode(username, StandardCharsets.UTF_8);
                req = mojangGet(url, Duration.ofSeconds(8));
                resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            }
            if (resp.statusCode() != 200 || resp.body() == null || resp.body().isBlank()) {
                return null;
            }
            JsonObject json = GSON.fromJson(resp.body(), JsonObject.class);
            if (json == null || !json.has("id")) {
                return null;
            }
            return dashUuid(json.get("id").getAsString());
        } catch (Exception e) {
            return null;
        }
    }

    static boolean retryableStatus(int code) {
        return code == 204 || code == 408 || code == 429 || code >= 500;
    }

    static Profile parseProfile(String body, String fallbackName) {
        JsonObject json = GSON.fromJson(body, JsonObject.class);
        String id = json.get("id").getAsString();
        UUID uuid = dashUuid(id);
        String name = json.has("name") ? json.get("name").getAsString() : fallbackName;
        List<ModernForwarding.Property> props = new ArrayList<>();
        if (json.has("properties")) {
            JsonArray arr = json.getAsJsonArray("properties");
            for (JsonElement el : arr) {
                JsonObject p = el.getAsJsonObject();
                props.add(new ModernForwarding.Property(
                        p.get("name").getAsString(),
                        p.get("value").getAsString(),
                        p.has("signature") ? p.get("signature").getAsString() : ""
                ));
            }
        }
        return new Profile(uuid, name, props);
    }

    private static HttpResponse<String> sendHasJoined(String username, String serverId) throws Exception {
        String url = "https://sessionserver.mojang.com/session/minecraft/hasJoined?username="
                + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&serverId=" + URLEncoder.encode(serverId, StandardCharsets.UTF_8);
        HttpRequest req = mojangGet(url, Duration.ofSeconds(8));
        return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpRequest mojangGet(String url, Duration timeout) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("User-Agent", "YaP-Link/0.6 (Minecraft hasJoined)")
                .header("Accept", "application/json")
                .GET()
                .build();
    }

    private static void sleepBackoff(int attempt) throws InterruptedException {
        Thread.sleep(200L * attempt);
    }

    private static UUID dashUuid(String undashed) {
        String s = undashed.replace("-", "");
        if (s.length() != 32) {
            throw new IllegalArgumentException("Bad UUID " + undashed);
        }
        return UUID.fromString(s.substring(0, 8) + "-" + s.substring(8, 12) + "-"
                + s.substring(12, 16) + "-" + s.substring(16, 20) + "-" + s.substring(20));
    }
}
