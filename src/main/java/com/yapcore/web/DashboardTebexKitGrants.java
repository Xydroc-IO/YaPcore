package com.yapcore.web;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pending / stuck store kit grants from YaPPlayerData {@code kit_grants} (shared YaPDB).
 */
public final class DashboardTebexKitGrants {

    private DashboardTebexKitGrants() {
    }

    public static Map<String, Object> snapshot(Path root) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pendingCount", 0);
        out.put("stuckCount", 0);
        out.put("pendingGrants", List.of());
        out.put("stuckGrants", List.of());
        out.put("dbOk", false);
        out.put("dbHint", "");

        JdbcCreds creds = resolveJdbc(root);
        if (creds == null || creds.url.isBlank()) {
            out.put("dbHint", "Configure YaPDB / YaPPlayerData JDBC to list pending kit grants.");
            return out;
        }
        Set<String> knownKits = knownKitIds(root);
        Map<String, String> names = nameIndex(root);
        try (Connection c = DriverManager.getConnection(creds.url, creds.user, creds.password)) {
            List<Map<String, Object>> pending = queryPending(c, knownKits, names, 50);
            List<Map<String, Object>> stuck = new ArrayList<>();
            List<Map<String, Object>> waiting = new ArrayList<>();
            for (Map<String, Object> row : pending) {
                if (Boolean.TRUE.equals(row.get("kitMissing"))) {
                    stuck.add(row);
                } else {
                    waiting.add(row);
                }
            }
            out.put("pendingGrants", waiting);
            out.put("stuckGrants", stuck);
            out.put("pendingCount", waiting.size());
            out.put("stuckCount", stuck.size());
            out.put("dbOk", true);
            out.put("dbHint", stuck.isEmpty()
                    ? (waiting.isEmpty() ? "No pending store kit grants." : "Waiting for player join.")
                    : "Stuck grants need kits.yml synced on every backend.");
        } catch (Exception e) {
            out.put("dbHint", e.getMessage() == null ? "kit_grants query failed" : e.getMessage());
        }
        return out;
    }

    public static boolean cancelGrant(Path root, long id) throws Exception {
        if (id <= 0) {
            throw new IllegalArgumentException("grant id required");
        }
        JdbcCreds creds = resolveJdbc(root);
        if (creds == null || creds.url.isBlank()) {
            throw new IllegalStateException("JDBC not configured");
        }
        try (Connection c = DriverManager.getConnection(creds.url, creds.user, creds.password);
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM kit_grants WHERE id = ? AND delivered_at IS NULL")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    private static List<Map<String, Object>> queryPending(
            Connection c, Set<String> knownKits, Map<String, String> names, int limit) throws Exception {
        List<Map<String, Object>> out = new ArrayList<>();
        String sql = """
                SELECT id, uuid, kit, created_at
                FROM kit_grants
                WHERE delivered_at IS NULL
                ORDER BY id ASC
                LIMIT ?
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    long id = rs.getLong("id");
                    String uuid = rs.getString("uuid");
                    String kit = rs.getString("kit");
                    Timestamp created = rs.getTimestamp("created_at");
                    boolean missing = kit == null || !knownKits.contains(kit.toLowerCase(Locale.ROOT));
                    row.put("id", id);
                    row.put("uuid", uuid);
                    row.put("kit", kit);
                    row.put("username", names.getOrDefault(
                            uuid == null ? "" : uuid.toLowerCase(Locale.ROOT), ""));
                    row.put("createdAt", created == null ? "" : created.toInstant().toString());
                    row.put("kitMissing", missing);
                    row.put("status", missing ? "kit-missing" : "pending");
                    out.add(row);
                }
            }
        }
        // Prefer players.name when available
        enrichNamesFromDb(c, out);
        return out;
    }

    private static void enrichNamesFromDb(Connection c, List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            if (!DashboardNetworkSnapshots.str(row.get("username"), "").isBlank()) {
                continue;
            }
            String uuid = DashboardNetworkSnapshots.str(row.get("uuid"), "");
            if (uuid.isBlank()) {
                continue;
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT name FROM players WHERE uuid = ?")) {
                ps.setString(1, uuid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        row.put("username", rs.getString("name"));
                    }
                }
            } catch (Exception ignored) {
                // table may not exist yet
            }
        }
    }

    private static Set<String> knownKitIds(Path root) {
        Set<String> out = new HashSet<>();
        for (Map<String, Object> kit : DashboardKits.listKits(root)) {
            String id = DashboardNetworkSnapshots.str(kit.get("id"), "").toLowerCase(Locale.ROOT);
            if (!id.isBlank()) {
                out.add(id);
            }
        }
        return out;
    }

    private static Map<String, String> nameIndex(Path root) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map<String, Object> row : DashboardSeenPlayers.load(root, List.of())) {
            String uuid = DashboardNetworkSnapshots.str(row.get("uuid"), "").toLowerCase(Locale.ROOT);
            String name = DashboardNetworkSnapshots.str(row.get("username"), "");
            if (!uuid.isBlank() && !name.isBlank()) {
                out.put(uuid, name);
            }
        }
        return out;
    }

    static JdbcCreds resolveJdbc(Path root) {
        Path yapdb = root.resolve("plugins").resolve("YaPDB").resolve("config.yml");
        Path playerdata = root.resolve("plugins").resolve("YaPPlayerData").resolve("config.yml");
        boolean useShared = true;
        if (Files.isRegularFile(playerdata)) {
            try {
                Map<String, Object> pd = DashboardNetworkSnapshots.loadYaml(playerdata);
                useShared = DashboardNetworkSnapshots.bool(pd.get("use-shared-yapdb"), true);
                if (!useShared) {
                    return fromJdbcMap(DashboardNetworkSnapshots.map(pd.get("jdbc")));
                }
            } catch (Exception ignored) {
            }
        }
        if (useShared && Files.isRegularFile(yapdb)) {
            try {
                Map<String, Object> db = DashboardNetworkSnapshots.loadYaml(yapdb);
                JdbcCreds shared = fromJdbcMap(DashboardNetworkSnapshots.map(db.get("jdbc")));
                if (shared != null && !shared.url.isBlank()) {
                    return shared;
                }
            } catch (Exception ignored) {
            }
        }
        if (Files.isRegularFile(playerdata)) {
            try {
                Map<String, Object> pd = DashboardNetworkSnapshots.loadYaml(playerdata);
                return fromJdbcMap(DashboardNetworkSnapshots.map(pd.get("jdbc")));
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static JdbcCreds fromJdbcMap(Map<String, Object> jdbc) {
        if (jdbc == null || jdbc.isEmpty()) {
            return null;
        }
        String url = DashboardNetworkSnapshots.str(jdbc.get("url"), "").trim();
        String user = DashboardNetworkSnapshots.str(jdbc.get("user"), "yap");
        String password = DashboardNetworkSnapshots.str(jdbc.get("password"), "");
        if (url.isBlank()) {
            return null;
        }
        return new JdbcCreds(url, user, password);
    }

    record JdbcCreds(String url, String user, String password) {
    }
}
