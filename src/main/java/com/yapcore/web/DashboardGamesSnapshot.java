package com.yapcore.web;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Snapshot + settings writer for {@code plugins/YaPGames} on the web dashboard. */
public final class DashboardGamesSnapshot {

    private static final String DATA_DIR = "YaPGames";

    private DashboardGamesSnapshot() {
    }

    public static boolean installed(Path pluginsDir) {
        return jarPresent(pluginsDir, "yap-games");
    }

    public static Map<String, Object> snapshot(Path rootDir) {
        Map<String, Object> out = new LinkedHashMap<>();
        Path pluginsDir = rootDir.resolve("plugins");
        out.put("installed", installed(pluginsDir));
        Path dataDir = pluginsDir.resolve(DATA_DIR);
        Path configFile = dataDir.resolve("config.yml");
        out.put("configPresent", Files.isRegularFile(configFile));
        out.put("modeCount", 0);
        out.put("arenaCount", 0);
        out.put("modes", List.of());
        out.put("arenas", List.of());
        out.put("leaderboardPreview", Map.of());

        if (!Files.isRegularFile(configFile)) {
            return out;
        }
        try {
            Map<String, Object> yaml = loadYaml(configFile);
            out.put("enabled", bool(yaml.get("enabled"), true));
            out.put("blockSkillXp", bool(nested(yaml, "match", "block-skill-xp"), true));
            out.put("rewardsEnabled", bool(nested(yaml, "rewards", "enabled"), true));
            out.put("countdownSeconds", intVal(yaml.get("countdown-seconds"), 10));
            out.put("lobbyWorld", str(yaml.get("lobby-world"), "world"));
            Path modesDir = dataDir.resolve(str(yaml.get("modes-directory"), "modes"));
            Path arenasDir = dataDir.resolve(str(yaml.get("arenas-directory"), "arenas"));
            List<String> modes = listYamlKeys(modesDir);
            List<String> arenas = listYamlKeys(arenasDir);
            out.put("modes", modes);
            out.put("arenas", arenas);
            out.put("modeCount", modes.size());
            out.put("arenaCount", arenas.size());
            String jdbcUrl = str(nested(yaml, "jdbc", "url"), "");
            String user = str(nested(yaml, "jdbc", "user"), "yap");
            String password = str(nested(yaml, "jdbc", "password"), "");
            out.put("leaderboardPreview", leaderboardPreview(jdbcUrl, user, password));
        } catch (IOException e) {
            out.put("error", e.getMessage() == null ? "config read failed" : e.getMessage());
        }
        return out;
    }

    public static void saveSettings(Path rootDir, Map<String, String> body) throws Exception {
        Path file = rootDir.resolve("plugins").resolve(DATA_DIR).resolve("config.yml");
        Map<String, Object> yaml = Files.isRegularFile(file)
                ? DashboardNetworkSnapshots.loadYaml(file)
                : new LinkedHashMap<>();
        if (body.containsKey("enabled")) {
            yaml.put("enabled", !"false".equalsIgnoreCase(body.get("enabled")));
        }
        if (body.containsKey("countdownSeconds")) {
            yaml.put("countdown-seconds", Integer.parseInt(body.get("countdownSeconds").trim()));
        }
        if (body.containsKey("lobbyWorld")) {
            yaml.put("lobby-world", body.get("lobbyWorld").trim());
        }
        Map<String, Object> match = DashboardNetworkSnapshots.mapOrCreate(yaml, "match");
        if (body.containsKey("blockSkillXp")) {
            match.put("block-skill-xp", !"false".equalsIgnoreCase(body.get("blockSkillXp")));
        }
        Map<String, Object> rewards = DashboardNetworkSnapshots.mapOrCreate(yaml, "rewards");
        if (body.containsKey("rewardsEnabled")) {
            rewards.put("enabled", !"false".equalsIgnoreCase(body.get("rewardsEnabled")));
        }
        Files.createDirectories(file.getParent());
        DashboardNetworkSnapshots.dumpYaml(file, yaml);
    }

    private static Map<String, List<Map<String, Object>>> leaderboardPreview(
            String jdbcUrl, String user, String password) {
        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return out;
        }
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ignored) {
            return out;
        }
        try (Connection c = DriverManager.getConnection(jdbcUrl, user, password)) {
            for (String mode : List.of("ffa", "duels")) {
                List<Map<String, Object>> rows = new ArrayList<>();
                try (PreparedStatement ps = c.prepareStatement("""
                        SELECT player_uuid, wins, kills, deaths FROM yap_games_stats
                        WHERE mode_id = ?
                        ORDER BY wins DESC, kills DESC
                        LIMIT 5
                        """)) {
                    ps.setString(1, mode);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("uuid", rs.getString("player_uuid"));
                            row.put("wins", rs.getInt("wins"));
                            row.put("kills", rs.getInt("kills"));
                            row.put("deaths", rs.getInt("deaths"));
                            rows.add(row);
                        }
                    }
                }
                out.put(mode, rows);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static List<String> listYamlKeys(Path dir) throws IOException {
        List<String> keys = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return keys;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.yml")) {
            for (Path file : stream) {
                Map<String, Object> yaml = loadYaml(file);
                for (String rootKey : yaml.keySet()) {
                    Object val = yaml.get(rootKey);
                    if (val instanceof Map<?, ?> map) {
                        for (Object k : map.keySet()) {
                            keys.add(String.valueOf(k));
                        }
                    }
                }
            }
        }
        return keys;
    }

    private static boolean jarPresent(Path pluginsDir, String prefix) {
        if (!Files.isDirectory(pluginsDir)) {
            return false;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginsDir, prefix + "*.jar")) {
            return stream.iterator().hasNext();
        } catch (IOException e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadYaml(Path file) throws IOException {
        Yaml yaml = new Yaml();
        try (InputStream in = Files.newInputStream(file)) {
            Object loaded = yaml.load(in);
            if (loaded instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
        }
        return Map.of();
    }

    private static Object nested(Map<String, Object> yaml, String... keys) {
        Object cur = yaml;
        for (String key : keys) {
            if (!(cur instanceof Map<?, ?> map)) {
                return null;
            }
            cur = map.get(key);
        }
        return cur;
    }

    private static boolean bool(Object val, boolean def) {
        if (val == null) {
            return def;
        }
        if (val instanceof Boolean b) {
            return b;
        }
        return !"false".equalsIgnoreCase(String.valueOf(val));
    }

    private static int intVal(Object val, int def) {
        if (val instanceof Number n) {
            return n.intValue();
        }
        if (val != null) {
            try {
                return Integer.parseInt(String.valueOf(val).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    private static String str(Object val, String def) {
        return val == null ? def : String.valueOf(val);
    }
}
