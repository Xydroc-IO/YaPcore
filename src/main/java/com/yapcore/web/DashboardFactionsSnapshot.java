package com.yapcore.web;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Read-only snapshot of {@code plugins/YaPFactions} for the web dashboard. */
public final class DashboardFactionsSnapshot {

    private static final String DATA_DIR = "YaPFactions";

    private DashboardFactionsSnapshot() {
    }

    public static boolean installed(Path pluginsDir) {
        return jarPresent(pluginsDir, "yap-factions");
    }

    public static Map<String, Object> snapshot(Path rootDir) {
        Map<String, Object> out = new LinkedHashMap<>();
        Path pluginsDir = rootDir.resolve("plugins");
        out.put("installed", installed(pluginsDir));
        Path configFile = pluginsDir.resolve(DATA_DIR).resolve("config.yml");
        out.put("configPresent", Files.isRegularFile(configFile));
        out.put("factions", 0);
        out.put("members", 0);
        out.put("claimOverlays", 0);
        out.put("alliances", 0);
        out.put("enemies", 0);
        out.put("preview", List.of());

        if (!Files.isRegularFile(configFile)) {
            return out;
        }
        try {
            Map<String, Object> yaml = loadYaml(configFile);
            out.put("enabled", bool(yaml.get("enabled"), true));
            out.put("baseMaxPower", intVal(nested(yaml, "power", "base-max"), 50));
            out.put("powerPerMember", intVal(nested(yaml, "power", "per-member"), 10));
            String jdbcUrl = str(nested(yaml, "jdbc", "url"), "");
            String user = str(nested(yaml, "jdbc", "user"), "yap");
            String password = str(nested(yaml, "jdbc", "password"), "");
            if (!jdbcUrl.isBlank()) {
                Map<String, Integer> counts = queryCounts(jdbcUrl, user, password);
                out.putAll(counts);
                out.put("preview", queryPreview(jdbcUrl, user, password));
            }
        } catch (IOException e) {
            out.put("error", e.getMessage() == null ? "config read failed" : e.getMessage());
        }
        return out;
    }

    private static Map<String, Integer> queryCounts(String jdbcUrl, String user, String password) {
        Map<String, Integer> out = new LinkedHashMap<>();
        try (Connection c = DriverManager.getConnection(jdbcUrl, user, password)) {
            out.put("factions", scalar(c, "SELECT COUNT(*) FROM yap_factions"));
            out.put("members", scalar(c, "SELECT COUNT(*) FROM yap_faction_members"));
            out.put("claimOverlays", scalar(c, "SELECT COUNT(*) FROM yap_faction_claims"));
            out.put("alliances", scalar(c, "SELECT COUNT(*) FROM yap_faction_relations WHERE relation = 'ALLY'"));
            out.put("enemies", scalar(c, "SELECT COUNT(*) FROM yap_faction_relations WHERE relation = 'ENEMY'"));
        } catch (Exception ignored) {
        }
        return out;
    }

    private static List<Map<String, Object>> queryPreview(String jdbcUrl, String user, String password) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(jdbcUrl, user, password);
             PreparedStatement ps = c.prepareStatement("""
                     SELECT id, name, tag, power, max_power FROM yap_factions ORDER BY name LIMIT 10
                     """)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("name", rs.getString("name"));
                    row.put("tag", rs.getString("tag"));
                    row.put("power", rs.getInt("power"));
                    row.put("maxPower", rs.getInt("max_power"));
                    rows.add(row);
                }
            }
        } catch (Exception ignored) {
        }
        return rows;
    }

    private static int scalar(Connection c, String sql) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadYaml(Path file) throws IOException {
        Yaml yaml = new Yaml();
        try (InputStream in = Files.newInputStream(file)) {
            Object loaded = yaml.load(in);
            if (loaded instanceof Map<?, ?> map) {
                return new LinkedHashMap<>((Map<String, Object>) map);
            }
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static Object nested(Map<String, Object> root, String... keys) {
        Object cur = root;
        for (String key : keys) {
            if (!(cur instanceof Map<?, ?> map)) {
                return null;
            }
            cur = map.get(key);
        }
        return cur;
    }

    private static String str(Object val, String fallback) {
        if (val == null) {
            return fallback;
        }
        String s = String.valueOf(val).trim();
        return s.isEmpty() ? fallback : s;
    }

    private static boolean bool(Object val, boolean fallback) {
        if (val instanceof Boolean b) {
            return b;
        }
        if (val != null) {
            return Boolean.parseBoolean(String.valueOf(val));
        }
        return fallback;
    }

    private static int intVal(Object val, int fallback) {
        if (val instanceof Number n) {
            return n.intValue();
        }
        if (val != null) {
            try {
                return Integer.parseInt(String.valueOf(val));
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static boolean jarPresent(Path pluginsDir, String token) {
        if (!Files.isDirectory(pluginsDir)) {
            return false;
        }
        try (var stream = Files.newDirectoryStream(pluginsDir, "*.jar")) {
            for (Path jar : stream) {
                String name = jar.getFileName().toString().toLowerCase(Locale.ROOT);
                if (name.contains(token)) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }
}
