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

/** Mojang sessionserver hasJoined for online-mode. */
public final class MojangAuth {

    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private MojangAuth() {
    }

    public record Profile(UUID id, String name, List<ModernForwarding.Property> properties) {
    }

    public static Profile hasJoined(String username, String serverId) throws Exception {
        String url = "https://sessionserver.mojang.com/session/minecraft/hasJoined?username="
                + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&serverId=" + URLEncoder.encode(serverId, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 204 || resp.body() == null || resp.body().isBlank()) {
            throw new IllegalStateException("Mojang auth failed (offline account or bad session)");
        }
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("Mojang auth HTTP " + resp.statusCode());
        }
        JsonObject json = GSON.fromJson(resp.body(), JsonObject.class);
        String id = json.get("id").getAsString();
        UUID uuid = dashUuid(id);
        String name = json.has("name") ? json.get("name").getAsString() : username;
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

    private static UUID dashUuid(String undashed) {
        String s = undashed.replace("-", "");
        if (s.length() != 32) {
            throw new IllegalArgumentException("Bad UUID " + undashed);
        }
        return UUID.fromString(s.substring(0, 8) + "-" + s.substring(8, 12) + "-"
                + s.substring(12, 16) + "-" + s.substring(16, 20) + "-" + s.substring(20));
    }
}
