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
import java.util.Locale;
import java.util.Map;

/** Read-only snapshot of {@code plugins/YaPSkills} for the web dashboard. */
public final class DashboardSkillsSnapshot {

    private static final String DATA_DIR = "YaPSkills";

    private DashboardSkillsSnapshot() {
    }

    public static boolean installed(Path pluginsDir) {
        return jarPresent(pluginsDir, "yap-skills");
    }

    public static Map<String, Object> snapshot(Path rootDir) {
        Map<String, Object> out = new LinkedHashMap<>();
        Path pluginsDir = rootDir.resolve("plugins");
        out.put("installed", installed(pluginsDir));
        Path dataDir = pluginsDir.resolve(DATA_DIR);
        Path configFile = dataDir.resolve("config.yml");
        out.put("configPresent", Files.isRegularFile(configFile));
        out.put("skillCount", 0);
        out.put("skills", List.of());
        out.put("combatLevelFormula", "OSRS-weighted: base + max(melee,ranged,magic)");
        out.put("leaderboardPreview", Map.of());
        out.put("onlineSample", List.of());

        if (!Files.isRegularFile(configFile)) {
            return out;
        }
        try {
            Map<String, Object> yaml = loadYaml(configFile);
            out.put("enabled", bool(yaml.get("enabled"), true));
            out.put("maxLevel", intVal(nested(yaml, "xp-table", "max-level"), 99));
            out.put("skillsDirectory", str(yaml.get("skills-directory"), "skills"));
            Path skillsDir = dataDir.resolve(str(yaml.get("skills-directory"), "skills"));
            List<Map<String, Object>> skills = listSkillPacks(skillsDir);
            out.put("skills", skills);
            out.put("skillCount", skills.size());
            out.put("leaderboardPreview", leaderboardPreview(yaml, skills));
        } catch (IOException e) {
            out.put("error", e.getMessage() == null ? "config read failed" : e.getMessage());
        }
        return out;
    }

    public static void enrichOnlineSample(
            Map<String, Object> snap,
            List<Map<String, Object>> onlinePlayers) {
        snap.put("onlineSample", onlinePlayers == null ? List.of() : onlinePlayers);
    }

    private static Map<String, List<Map<String, Object>>> leaderboardPreview(
            Map<String, Object> configYaml,
            List<Map<String, Object>> skills) throws IOException {
        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
        if (skills.isEmpty()) {
            return out;
        }
        String jdbcUrl = str(nested(configYaml, "jdbc", "url"), "");
        if (jdbcUrl.isBlank()) {
            return out;
        }
        String user = str(nested(configYaml, "jdbc", "user"), "yap");
        String password = str(nested(configYaml, "jdbc", "password"), "");
        for (Map<String, Object> skill : skills) {
            String id = str(skill.get("id"), "");
            if (id.isBlank()) {
                continue;
            }
            out.put(id, queryTop(jdbcUrl, user, password, id, 3));
        }
        return out;
    }

    private static List<Map<String, Object>> queryTop(
            String jdbcUrl, String user, String password, String skillId, int limit) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(jdbcUrl, user, password);
             PreparedStatement ps = c.prepareStatement("""
                     SELECT player_uuid, level, xp FROM yap_skill_progress
                     WHERE skill_id = ?
                     ORDER BY level DESC, xp DESC
                     LIMIT ?
                     """)) {
            ps.setString(1, skillId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("playerId", rs.getString("player_uuid"));
                    row.put("level", rs.getInt("level"));
                    row.put("xp", rs.getDouble("xp"));
                    rows.add(row);
                }
            }
        } catch (Exception ignored) {
            // DB may be offline — snapshot stays read-only without crashing
        }
        return rows;
    }

    private static List<Map<String, Object>> listSkillPacks(Path skillsDir) throws IOException {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!Files.isDirectory(skillsDir)) {
            return out;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(skillsDir, "*.yml")) {
            for (Path file : stream) {
                Map<String, Object> yaml = loadYaml(file);
                Map<String, Object> row = new LinkedHashMap<>();
                String id = str(yaml.get("id"), file.getFileName().toString().replace(".yml", ""));
                row.put("id", id);
                row.put("display", str(yaml.get("display"), id));
                row.put("enabled", bool(yaml.get("enabled"), true));
                out.add(row);
            }
        }
        out.sort((a, b) -> str(a.get("id"), "").compareTo(str(b.get("id"), "")));
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadYaml(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return new LinkedHashMap<>();
        }
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
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginsDir, "*.jar")) {
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
