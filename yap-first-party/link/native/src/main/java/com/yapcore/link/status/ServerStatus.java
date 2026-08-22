package com.yapcore.link.status;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Parsed Minecraft server list ping response.
 */
public record ServerStatus(
        String rawJson,
        String versionName,
        int protocol,
        int online,
        int max,
        String descriptionText
) {
    private static final Gson GSON = new Gson();

    public static ServerStatus synthetic(String motd, int online, int max, int protocol, String versionName) {
        JsonObject root = new JsonObject();
        JsonObject version = new JsonObject();
        version.addProperty("name", versionName);
        version.addProperty("protocol", protocol);
        root.add("version", version);
        JsonObject players = new JsonObject();
        players.addProperty("max", max);
        players.addProperty("online", online);
        players.add("sample", new JsonArray());
        root.add("players", players);
        JsonObject desc = new JsonObject();
        desc.addProperty("text", motd);
        root.add("description", desc);
        String json = GSON.toJson(root);
        return new ServerStatus(json, versionName, protocol, online, max, motd);
    }

    public static ServerStatus parseJson(String json) {
        JsonObject root = GSON.fromJson(json, JsonObject.class);
        String versionName = "Unknown";
        int protocol = 0;
        if (root.has("version") && root.get("version").isJsonObject()) {
            JsonObject v = root.getAsJsonObject("version");
            if (v.has("name")) {
                versionName = v.get("name").getAsString();
            }
            if (v.has("protocol")) {
                protocol = v.get("protocol").getAsInt();
            }
        }
        int online = 0;
        int max = 0;
        if (root.has("players") && root.get("players").isJsonObject()) {
            JsonObject p = root.getAsJsonObject("players");
            if (p.has("online")) {
                online = p.get("online").getAsInt();
            }
            if (p.has("max")) {
                max = p.get("max").getAsInt();
            }
        }
        String desc = "";
        if (root.has("description")) {
            desc = textFromDescription(root.get("description"));
        }
        return new ServerStatus(json, versionName, protocol, online, max, desc);
    }

    public ServerStatus mergeCounts(int addOnline, int newMax) {
        return new ServerStatus(
                rawJson,
                versionName,
                protocol,
                online + addOnline,
                Math.max(max, newMax),
                descriptionText
        );
    }

    public String toStatusJson(int overrideOnline, int overrideMax) {
        JsonObject root = GSON.fromJson(rawJson, JsonObject.class);
        JsonObject players = root.has("players") && root.get("players").isJsonObject()
                ? root.getAsJsonObject("players")
                : new JsonObject();
        players.addProperty("online", overrideOnline);
        players.addProperty("max", overrideMax);
        if (!players.has("sample")) {
            players.add("sample", new JsonArray());
        }
        root.add("players", players);
        return GSON.toJson(root);
    }

    private static String textFromDescription(com.google.gson.JsonElement el) {
        if (el == null || el.isJsonNull()) {
            return "";
        }
        if (el.isJsonPrimitive()) {
            return el.getAsString();
        }
        if (el.isJsonObject()) {
            JsonObject o = el.getAsJsonObject();
            if (o.has("text")) {
                return o.get("text").getAsString();
            }
        }
        return el.toString();
    }
}
