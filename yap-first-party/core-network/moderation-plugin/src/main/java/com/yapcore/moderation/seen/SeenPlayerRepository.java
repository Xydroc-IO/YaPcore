package com.yapcore.moderation.seen;

import com.yapcore.db.YapDbEngine;
import com.yapcore.db.YapSqlDialect;
import com.yapcore.moderation.db.ModerationDatabase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Every player who has joined: username, nickname, last IP, seen times. */
public final class SeenPlayerRepository {

    public record SeenPlayer(UUID uuid, String username, String nickname, String lastIp,
                             long firstSeen, long lastSeen) {
    }

    private final ModerationDatabase database;
    private final YapSqlDialect dialect;

    public SeenPlayerRepository(ModerationDatabase database) {
        this.database = database;
        this.dialect = database.dialect();
    }

    public void migrate() throws SQLException {
        try (Connection c = database.connection(); Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_seen_players (
                      uuid CHAR(36) PRIMARY KEY,
                      username VARCHAR(16) NOT NULL,
                      nickname VARCHAR(64) NOT NULL DEFAULT '',
                      last_ip VARCHAR(45) NOT NULL DEFAULT '',
                      first_seen BIGINT NOT NULL,
                      last_seen BIGINT NOT NULL
                    )
                    """);
            createIndex(st, "idx_seen_name", "yap_seen_players", "username");
            createIndex(st, "idx_seen_ip", "yap_seen_players", "last_ip");
            createIndex(st, "idx_seen_last", "yap_seen_players", "last_seen");
            st.execute(seedFromKnownIpsSql());
        }
    }

    private String seedFromKnownIpsSql() {
        String select = """
                SELECT k.uuid, '', '', k.ip_address, k.last_seen, k.last_seen
                FROM yap_mod_known_ips k
                INNER JOIN (
                  SELECT uuid, MAX(last_seen) AS mx FROM yap_mod_known_ips GROUP BY uuid
                ) latest ON latest.uuid = k.uuid AND latest.mx = k.last_seen
                """;
        return switch (dialect.engine()) {
            case MYSQL -> """
                    INSERT IGNORE INTO yap_seen_players
                      (uuid, username, nickname, last_ip, first_seen, last_seen)
                    """ + select;
            case POSTGRES -> """
                    INSERT INTO yap_seen_players
                      (uuid, username, nickname, last_ip, first_seen, last_seen)
                    """ + select + " ON CONFLICT DO NOTHING";
            case SQLITE -> """
                    INSERT OR IGNORE INTO yap_seen_players
                      (uuid, username, nickname, last_ip, first_seen, last_seen)
                    """ + select;
        };
    }

    private void createIndex(Statement st, String name, String table, String cols) {
        try {
            String sql = dialect.engine() == YapDbEngine.MYSQL
                    ? "CREATE INDEX " + name + " ON " + table + " (" + cols + ")"
                    : "CREATE INDEX IF NOT EXISTS " + name + " ON " + table + " (" + cols + ")";
            st.execute(sql);
        } catch (SQLException ignored) {
            // already exists
        }
    }

    public void record(UUID uuid, String username, String nickname, String ip) throws SQLException {
        if (uuid == null) {
            return;
        }
        long now = System.currentTimeMillis();
        String name = truncate(username, 16);
        String nick = truncate(nickname == null ? "" : nickname, 64);
        String lastIp = ip == null ? "" : ip.trim();
        String sql = dialect.upsert(
                "yap_seen_players",
                List.of("uuid"),
                List.of("uuid", "username", "nickname", "last_ip", "first_seen", "last_seen"),
                Map.of(
                        "username", "EXCLUDED.username",
                        "nickname", "CASE WHEN EXCLUDED.nickname = '' THEN nickname ELSE EXCLUDED.nickname END",
                        "last_ip", "CASE WHEN EXCLUDED.last_ip = '' THEN last_ip ELSE EXCLUDED.last_ip END",
                        "last_seen", "EXCLUDED.last_seen"));
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setString(3, nick);
            ps.setString(4, lastIp);
            ps.setLong(5, now);
            ps.setLong(6, now);
            ps.executeUpdate();
        }
    }

    public Optional<SeenPlayer> find(UUID uuid) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT uuid, username, nickname, last_ip, first_seen, last_seen FROM yap_seen_players WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(row(rs));
            }
        }
    }

    public Optional<SeenPlayer> findByNameOrUuid(String token) throws SQLException {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String raw = token.trim();
        try {
            return find(UUID.fromString(raw));
        } catch (IllegalArgumentException ignored) {
        }
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT uuid, username, nickname, last_ip, first_seen, last_seen FROM yap_seen_players WHERE username = ? LIMIT 1")) {
            ps.setString(1, raw);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(row(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<SeenPlayer> list(int limit) throws SQLException {
        int cap = Math.min(20000, Math.max(1, limit));
        List<SeenPlayer> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT uuid, username, nickname, last_ip, first_seen, last_seen FROM yap_seen_players ORDER BY last_seen DESC LIMIT ?")) {
            ps.setInt(1, cap);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(row(rs));
                }
            }
        }
        return out;
    }

    public void writeSnapshot(Path file) throws SQLException, IOException {
        List<SeenPlayer> rows = list(20000);
        Map<String, String> knownIps = knownIpsByUuid();
        StringBuilder sb = new StringBuilder(256 + rows.size() * 160);
        sb.append("{\"players\":[");
        for (int i = 0; i < rows.size(); i++) {
            SeenPlayer p = rows.get(i);
            if (i > 0) {
                sb.append(',');
            }
            String ips = knownIps.getOrDefault(p.uuid().toString(), p.lastIp());
            sb.append('{')
                    .append("\"uuid\":\"").append(esc(p.uuid().toString())).append("\",")
                    .append("\"username\":\"").append(esc(p.username())).append("\",")
                    .append("\"nickname\":\"").append(esc(p.nickname())).append("\",")
                    .append("\"ip\":\"").append(esc(p.lastIp())).append("\",")
                    .append("\"ips\":\"").append(esc(ips)).append("\",")
                    .append("\"firstSeen\":").append(p.firstSeen()).append(',')
                    .append("\"lastSeen\":").append(p.lastSeen())
                    .append('}');
        }
        sb.append("]}");
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    public Map<String, String> knownIpsByUuid() throws SQLException {
        Map<String, String> out = new LinkedHashMap<>();
        String aggSql = switch (dialect.engine()) {
            case MYSQL -> "SELECT uuid, GROUP_CONCAT(ip_address ORDER BY last_seen DESC SEPARATOR ',') AS ips "
                    + "FROM yap_mod_known_ips GROUP BY uuid";
            case POSTGRES -> "SELECT uuid, string_agg(ip_address, ',' ORDER BY last_seen DESC) AS ips "
                    + "FROM yap_mod_known_ips GROUP BY uuid";
            case SQLITE -> "SELECT uuid, GROUP_CONCAT(ip_address) AS ips "
                    + "FROM (SELECT uuid, ip_address FROM yap_mod_known_ips ORDER BY last_seen DESC) "
                    + "GROUP BY uuid";
        };
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(aggSql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String uuid = rs.getString("uuid");
                String ips = rs.getString("ips");
                if (uuid != null && ips != null && !ips.isBlank()) {
                    out.put(uuid, ips);
                }
            }
        } catch (SQLException e) {
            return out;
        }
        return out;
    }

    public String toJsonArray(int limit) throws SQLException {
        List<SeenPlayer> rows = list(limit);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < rows.size(); i++) {
            SeenPlayer p = rows.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"uuid\":\"").append(esc(p.uuid().toString()))
                    .append("\",\"username\":\"").append(esc(p.username()))
                    .append("\",\"nickname\":\"").append(esc(p.nickname()))
                    .append("\",\"ip\":\"").append(esc(p.lastIp()))
                    .append("\",\"firstSeen\":").append(p.firstSeen())
                    .append(",\"lastSeen\":").append(p.lastSeen()).append('}');
        }
        return sb.append(']').toString();
    }

    public void seedName(UUID uuid, String username) throws SQLException {
        if (uuid == null || username == null || username.isBlank()) {
            return;
        }
        String name = truncate(username, 16);
        String sql = dialect.insertIgnore(
                "yap_seen_players",
                List.of("uuid", "username", "nickname", "last_ip", "first_seen", "last_seen"));
        try (Connection c = database.connection();
             PreparedStatement ins = c.prepareStatement(sql)) {
            ins.setString(1, uuid.toString());
            ins.setString(2, name);
            ins.setString(3, "");
            ins.setString(4, "");
            ins.setLong(5, 0L);
            ins.setLong(6, 0L);
            ins.executeUpdate();
        }
        applyUsername(uuid, name);
    }

    public void applyUsername(UUID uuid, String username) throws SQLException {
        if (uuid == null || username == null || username.isBlank()) {
            return;
        }
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE yap_seen_players SET username = ? WHERE uuid = ? AND (username = '' OR username = 'unknown')")) {
            ps.setString(1, truncate(username, 16));
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        }
    }

    private static SeenPlayer row(ResultSet rs) throws SQLException {
        return new SeenPlayer(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("username"),
                rs.getString("nickname"),
                rs.getString("last_ip"),
                rs.getLong("first_seen"),
                rs.getLong("last_seen"));
    }

    private static String truncate(String raw, int max) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String v = raw.trim();
        return v.length() <= max ? v : v.substring(0, max);
    }

    private static String esc(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }

    public static String stripColor(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("[§&][0-9a-fk-orA-FK-OR]", "").replaceAll("&#[0-9a-fA-F]{6}", "").trim();
    }

    public static String nicknameOrEmpty(String username, String displayName) {
        String nick = stripColor(displayName);
        if (nick.isEmpty() || nick.equalsIgnoreCase(username)) {
            return "";
        }
        return nick;
    }

    public static boolean looksLikeIp(String token) {
        if (token == null) {
            return false;
        }
        String t = token.trim();
        if (t.contains(":") && t.chars().filter(ch -> ch == ':').count() >= 2) {
            return true;
        }
        String[] parts = t.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        try {
            for (String p : parts) {
                int n = Integer.parseInt(p);
                if (n < 0 || n > 255) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static UUID parseUuid(String token) {
        if (token == null) {
            return null;
        }
        try {
            return UUID.fromString(token.trim());
        } catch (IllegalArgumentException e) {
            String compact = token.trim().replace("-", "").toLowerCase(Locale.ROOT);
            if (compact.length() == 32 && compact.chars().allMatch(ch -> (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f'))) {
                return UUID.fromString(compact.substring(0, 8) + "-" + compact.substring(8, 12)
                        + "-" + compact.substring(12, 16) + "-" + compact.substring(16, 20)
                        + "-" + compact.substring(20));
            }
            return null;
        }
    }
}
