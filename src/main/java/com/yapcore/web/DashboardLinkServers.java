package com.yapcore.web;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

/** Backend server / try / forced-host parse + save for {@link DashboardLinkSnapshot}. */
final class DashboardLinkServers {

    private static final Pattern SERVER_NAME = Pattern.compile("[a-zA-Z][a-zA-Z0-9_-]*");
    private static final Pattern HOST_PORT = Pattern.compile(".+:\\d{1,5}");

    private DashboardLinkServers() {
    }

    /** Replaces all {@code servers.*} and {@code forced-host.*} keys. */
    static void saveServersConfig(
            Path rootDir,
            String linkEmbedHome,
            List<Map<String, String>> servers,
            List<String> tryOrder,
            List<Map<String, String>> forcedHosts
    ) throws IOException {
        if (servers == null || servers.isEmpty()) {
            throw new IOException("At least one backend server is required");
        }
        Path linkProps = DashboardLinkSnapshot.resolveHome(rootDir, linkEmbedHome).resolve("link.properties");
        Properties props = DashboardLinkProps.load(linkProps);
        List<String> remove = props.stringPropertyNames().stream()
                .filter(k -> k.startsWith("servers.") || k.startsWith("forced-host."))
                .toList();
        for (String key : remove) {
            props.remove(key);
        }

        Set<String> names = new LinkedHashSet<>();
        for (Map<String, String> server : servers) {
            String name = normalizeServerName(server.get("name"));
            String address = normalizeAddress(server.get("address"));
            if (!names.add(name)) {
                throw new IOException("Duplicate server name: " + name);
            }
            props.setProperty("servers." + name, address);
            String bedrock = server.get("bedrock");
            if (bedrock != null && !bedrock.isBlank()) {
                props.setProperty("servers." + name + ".bedrock", normalizeAddress(bedrock));
            }
        }

        List<String> tryList = new ArrayList<>();
        if (tryOrder == null || tryOrder.isEmpty()) {
            tryList.addAll(names);
        } else {
            for (String raw : tryOrder) {
                tryList.add(normalizeServerName(raw));
            }
        }
        for (String name : tryList) {
            if (!names.contains(name)) {
                throw new IOException("try order references unknown server: " + name);
            }
        }
        props.setProperty("try", String.join(",", tryList));

        if (forcedHosts != null) {
            for (Map<String, String> entry : forcedHosts) {
                String host = entry.get("host");
                if (host == null || host.isBlank()) {
                    continue;
                }
                String target = normalizeServerName(entry.get("server"));
                if (!names.contains(target)) {
                    throw new IOException("forced-host targets unknown server: " + target);
                }
                props.setProperty("forced-host." + host.trim().toLowerCase(Locale.ROOT), target);
            }
        }
        DashboardLinkProps.store(linkProps, props);
    }

    /** Parses POST body for {@code save-servers} (Gson). */
    static void saveServersFromJson(Path rootDir, String linkEmbedHome, String jsonBody) throws IOException {
        JsonObject root = JsonParser.parseString(jsonBody == null ? "{}" : jsonBody).getAsJsonObject();
        List<Map<String, String>> servers = parseServerArray(root.get("servers"));
        List<String> tryOrder = parseStringArray(root.get("try"));
        List<Map<String, String>> forced = parseForcedArray(root.get("forcedHosts"));
        saveServersConfig(rootDir, linkEmbedHome, servers, tryOrder, forced);
    }

    static List<Map<String, Object>> parseServers(Properties props) {
        List<Map<String, Object>> servers = new ArrayList<>();
        for (String key : props.stringPropertyNames()) {
            if (!key.startsWith("servers.")) {
                continue;
            }
            String rest = key.substring("servers.".length());
            if (rest.contains(".")) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", rest);
            entry.put("address", props.getProperty(key, ""));
            String bedrock = props.getProperty("servers." + rest + ".bedrock", "");
            if (bedrock != null && !bedrock.isBlank()) {
                entry.put("bedrock", bedrock);
            }
            servers.add(entry);
        }
        servers.sort((a, b) -> String.valueOf(a.get("name")).compareToIgnoreCase(String.valueOf(b.get("name"))));
        return servers;
    }

    static List<String> parseTry(Properties props) {
        String raw = props.getProperty("try", "");
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    static List<Map<String, Object>> parseForcedHosts(Properties props) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String key : props.stringPropertyNames()) {
            if (!key.startsWith("forced-host.")) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("host", key.substring("forced-host.".length()));
            entry.put("server", props.getProperty(key, ""));
            out.add(entry);
        }
        out.sort((a, b) -> String.valueOf(a.get("host")).compareToIgnoreCase(String.valueOf(b.get("host"))));
        return out;
    }

    private static List<Map<String, String>> parseServerArray(JsonElement el) {
        List<Map<String, String>> out = new ArrayList<>();
        if (el == null || !el.isJsonArray()) {
            return out;
        }
        for (JsonElement item : el.getAsJsonArray()) {
            if (!item.isJsonObject()) {
                continue;
            }
            JsonObject o = item.getAsJsonObject();
            Map<String, String> row = new LinkedHashMap<>();
            row.put("name", jsonString(o, "name"));
            row.put("address", jsonString(o, "address"));
            row.put("bedrock", jsonString(o, "bedrock"));
            out.add(row);
        }
        return out;
    }

    private static List<Map<String, String>> parseForcedArray(JsonElement el) {
        List<Map<String, String>> out = new ArrayList<>();
        if (el == null || !el.isJsonArray()) {
            return out;
        }
        for (JsonElement item : el.getAsJsonArray()) {
            if (!item.isJsonObject()) {
                continue;
            }
            JsonObject o = item.getAsJsonObject();
            Map<String, String> row = new LinkedHashMap<>();
            row.put("host", jsonString(o, "host"));
            row.put("server", jsonString(o, "server"));
            out.add(row);
        }
        return out;
    }

    private static List<String> parseStringArray(JsonElement el) {
        List<String> out = new ArrayList<>();
        if (el == null || !el.isJsonArray()) {
            return out;
        }
        for (JsonElement item : el.getAsJsonArray()) {
            if (item.isJsonPrimitive()) {
                String s = item.getAsString().trim();
                if (!s.isEmpty()) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    private static String jsonString(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return "";
        }
        return o.get(key).getAsString();
    }

    private static String normalizeServerName(String raw) throws IOException {
        if (raw == null || raw.isBlank()) {
            throw new IOException("Server name required");
        }
        String name = raw.trim().toLowerCase(Locale.ROOT);
        if (!SERVER_NAME.matcher(name).matches()) {
            throw new IOException("Invalid server name: " + raw);
        }
        return name;
    }

    private static String normalizeAddress(String raw) throws IOException {
        if (raw == null || raw.isBlank()) {
            throw new IOException("host:port address required");
        }
        String address = raw.trim();
        if (!HOST_PORT.matcher(address).matches()) {
            throw new IOException("Invalid address (expected host:port): " + raw);
        }
        return address;
    }
}
