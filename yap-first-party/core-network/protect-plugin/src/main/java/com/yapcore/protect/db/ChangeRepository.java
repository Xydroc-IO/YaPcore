package com.yapcore.protect.db;

import com.yapcore.protect.ProtectLookupCursor;
import com.yapcore.protect.model.ChangeType;
import com.yapcore.protect.model.ProtectChange;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ChangeRepository {

    private static final String SELECT_COLS =
            "id, server_id, change_type, actor_uuid, actor_name, world, x, y, z, "
                    + "block_before, block_after, epoch_ms, rolled_back";

    private final SqlConnectionSource database;
    private volatile String serverId;

    public ChangeRepository(ProtectDatabase database) {
        this(database::connection, "lobby");
    }

    /** Test / alternate pools (e.g. in-memory SQLite). */
    ChangeRepository(SqlConnectionSource database) {
        this(database, "default");
    }

    ChangeRepository(SqlConnectionSource database, String serverId) {
        this.database = database;
        this.serverId = serverId == null || serverId.isBlank() ? "default" : serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId == null || serverId.isBlank() ? "default" : serverId;
    }

    public String serverId() {
        return serverId;
    }

    @FunctionalInterface
    interface SqlConnectionSource {
        Connection connection() throws SQLException;
    }

    public long insert(String serverId, ChangeType type, UUID actorUuid, String actorName,
                       String world, int x, int y, int z, String before, String after) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO yap_protect_changes
                       (server_id, change_type, actor_uuid, actor_name, world, x, y, z,
                        block_before, block_after, epoch_ms)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, serverId);
            ps.setString(2, type.name());
            ps.setString(3, actorUuid == null ? null : actorUuid.toString());
            ps.setString(4, actorName);
            ps.setString(5, world);
            ps.setInt(6, x);
            ps.setInt(7, y);
            ps.setInt(8, z);
            ps.setString(9, truncate(before));
            ps.setString(10, truncate(after));
            ps.setLong(11, System.currentTimeMillis());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        return -1L;
    }

    public List<ProtectChange> lookupActor(UUID actorUuid, long fromMs, long toMs, int limit) throws SQLException {
        List<ProtectChange> rows = lookupActorPage(actorUuid, fromMs, toMs, limit, null);
        return rows.size() > limit ? rows.subList(0, limit) : rows;
    }

    public List<ProtectChange> lookupActorPage(UUID actorUuid, long fromMs, long toMs, int limit,
                                               ProtectLookupCursor after) throws SQLException {
        String sql = """
                SELECT %s
                FROM yap_protect_changes
                WHERE server_id = ? AND actor_uuid = ? AND epoch_ms BETWEEN ? AND ?
                """.formatted(SELECT_COLS) + cursorClause(after) + """
                 ORDER BY epoch_ms DESC, id DESC LIMIT ?
                """;
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, serverId);
            ps.setString(i++, actorUuid.toString());
            ps.setLong(i++, fromMs);
            ps.setLong(i++, toMs);
            i = bindCursor(ps, i, after);
            ps.setInt(i, pageLimit(limit));
            return readAll(ps);
        }
    }

    public List<ProtectChange> lookupBlock(String world, int x, int y, int z,
                                           long fromMs, long toMs, int limit) throws SQLException {
        List<ProtectChange> rows = lookupBlockPage(world, x, y, z, fromMs, toMs, limit, null);
        return rows.size() > limit ? rows.subList(0, limit) : rows;
    }

    public List<ProtectChange> lookupBlockPage(String world, int x, int y, int z,
                                               long fromMs, long toMs, int limit,
                                               ProtectLookupCursor after) throws SQLException {
        String sql = """
                SELECT %s
                FROM yap_protect_changes
                WHERE server_id = ? AND world = ? AND x = ? AND y = ? AND z = ?
                  AND epoch_ms BETWEEN ? AND ?
                """.formatted(SELECT_COLS) + cursorClause(after) + """
                 ORDER BY epoch_ms DESC, id DESC LIMIT ?
                """;
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, serverId);
            ps.setString(i++, world);
            ps.setInt(i++, x);
            ps.setInt(i++, y);
            ps.setInt(i++, z);
            ps.setLong(i++, fromMs);
            ps.setLong(i++, toMs);
            i = bindCursor(ps, i, after);
            ps.setInt(i, pageLimit(limit));
            return readAll(ps);
        }
    }

    public List<ProtectChange> lookupRadius(String world, int cx, int cy, int cz, int radius,
                                           long fromMs, long toMs, int limit) throws SQLException {
        List<ProtectChange> rows = lookupRadiusPage(world, cx, cy, cz, radius, fromMs, toMs, limit, null);
        return rows.size() > limit ? rows.subList(0, limit) : rows;
    }

    public List<ProtectChange> lookupRadiusPage(String world, int cx, int cy, int cz, int radius,
                                                long fromMs, long toMs, int limit,
                                                ProtectLookupCursor after) throws SQLException {
        int r = Math.max(0, radius);
        String sql = """
                SELECT %s
                FROM yap_protect_changes
                WHERE server_id = ? AND world = ?
                  AND x BETWEEN ? AND ?
                  AND y BETWEEN ? AND ?
                  AND z BETWEEN ? AND ?
                  AND epoch_ms BETWEEN ? AND ?
                """.formatted(SELECT_COLS) + cursorClause(after) + """
                 ORDER BY epoch_ms DESC, id DESC LIMIT ?
                """;
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, serverId);
            ps.setString(i++, world);
            ps.setInt(i++, cx - r);
            ps.setInt(i++, cx + r);
            ps.setInt(i++, cy - r);
            ps.setInt(i++, cy + r);
            ps.setInt(i++, cz - r);
            ps.setInt(i++, cz + r);
            ps.setLong(i++, fromMs);
            ps.setLong(i++, toMs);
            i = bindCursor(ps, i, after);
            ps.setInt(i, pageLimit(limit));
            return readAll(ps);
        }
    }

    public List<ProtectChange> lookupTimeRange(String world, long fromMs, long toMs, int limit) throws SQLException {
        List<ProtectChange> rows = lookupTimeRangePage(world, fromMs, toMs, limit, null);
        return rows.size() > limit ? rows.subList(0, limit) : rows;
    }

    public List<ProtectChange> lookupTimeRangePage(String world, long fromMs, long toMs, int limit,
                                                   ProtectLookupCursor after) throws SQLException {
        String sql = """
                SELECT %s
                FROM yap_protect_changes
                WHERE server_id = ? AND world = ? AND epoch_ms BETWEEN ? AND ?
                """.formatted(SELECT_COLS) + cursorClause(after) + """
                 ORDER BY epoch_ms DESC, id DESC LIMIT ?
                """;
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, serverId);
            ps.setString(i++, world);
            ps.setLong(i++, fromMs);
            ps.setLong(i++, toMs);
            i = bindCursor(ps, i, after);
            ps.setInt(i, pageLimit(limit));
            return readAll(ps);
        }
    }

    /** Uncapped (batched) fetch for bulk rollback — not for UI lookup. */
    public List<ProtectChange> lookupActorAll(UUID actorUuid, long fromMs, long toMs) throws SQLException {
        return fetchAll(cursor -> lookupActorPage(actorUuid, fromMs, toMs, 500, cursor));
    }

    public List<ProtectChange> lookupRadiusAll(String world, int cx, int cy, int cz, int radius,
                                               long fromMs, long toMs) throws SQLException {
        return fetchAll(cursor -> lookupRadiusPage(world, cx, cy, cz, radius, fromMs, toMs, 500, cursor));
    }

    public List<ProtectChange> lookupTimeRangeAll(String world, long fromMs, long toMs) throws SQLException {
        return fetchAll(cursor -> lookupTimeRangePage(world, fromMs, toMs, 500, cursor));
    }

    @FunctionalInterface
    private interface PageFetch {
        List<ProtectChange> fetch(ProtectLookupCursor cursor) throws SQLException;
    }

    private static List<ProtectChange> fetchAll(PageFetch fetch) throws SQLException {
        List<ProtectChange> all = new ArrayList<>();
        ProtectLookupCursor cursor = null;
        while (true) {
            List<ProtectChange> page = fetch.fetch(cursor);
            if (page.isEmpty()) {
                break;
            }
            boolean more = page.size() > 500;
            List<ProtectChange> slice = more ? page.subList(0, 500) : page;
            all.addAll(slice);
            if (!more) {
                break;
            }
            ProtectChange last = slice.get(slice.size() - 1);
            cursor = new ProtectLookupCursor(last.epochMs(), last.id());
        }
        return all;
    }

    private static String cursorClause(ProtectLookupCursor after) {
        if (after == null) {
            return "";
        }
        return " AND (epoch_ms < ? OR (epoch_ms = ? AND id < ?)) ";
    }

    private static int bindCursor(PreparedStatement ps, int i, ProtectLookupCursor after) throws SQLException {
        if (after == null) {
            return i;
        }
        ps.setLong(i++, after.epochMs());
        ps.setLong(i++, after.epochMs());
        ps.setLong(i++, after.id());
        return i;
    }

    private static int pageLimit(int limit) {
        return Math.max(1, Math.min(limit + 1, 501));
    }

    public List<ProtectChange> lookupActorInRadius(UUID actorUuid, String world, int cx, int cy, int cz,
                                                   int radius, long fromMs, long toMs, int limit) throws SQLException {
        int r = Math.max(0, radius);
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT %s
                     FROM yap_protect_changes
                     WHERE server_id = ? AND actor_uuid = ? AND world = ?
                       AND x BETWEEN ? AND ?
                       AND y BETWEEN ? AND ?
                       AND z BETWEEN ? AND ?
                       AND epoch_ms BETWEEN ? AND ?
                     ORDER BY epoch_ms DESC LIMIT ?
                     """.formatted(SELECT_COLS))) {
            ps.setString(1, serverId);
            ps.setString(2, actorUuid.toString());
            ps.setString(3, world);
            ps.setInt(4, cx - r);
            ps.setInt(5, cx + r);
            ps.setInt(6, cy - r);
            ps.setInt(7, cy + r);
            ps.setInt(8, cz - r);
            ps.setInt(9, cz + r);
            ps.setLong(10, fromMs);
            ps.setLong(11, toMs);
            ps.setInt(12, Math.max(1, Math.min(limit, 500)));
            return readAll(ps);
        }
    }

    public List<ProtectChange> fetchByIds(List<Long> ids) throws SQLException {
        if (ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT " + SELECT_COLS
                             + " FROM yap_protect_changes WHERE server_id = ? AND id IN ("
                             + placeholders + ") ORDER BY epoch_ms ASC")) {
            ps.setString(1, serverId);
            for (int i = 0; i < ids.size(); i++) {
                ps.setLong(i + 2, ids.get(i));
            }
            return readAll(ps);
        }
    }

    public void markRolledBack(List<Long> ids) throws SQLException {
        if (ids.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE yap_protect_changes SET rolled_back = ? WHERE server_id = ? AND id IN ("
                             + placeholders + ")")) {
            ps.setBoolean(1, true);
            ps.setString(2, serverId);
            for (int i = 0; i < ids.size(); i++) {
                ps.setLong(i + 3, ids.get(i));
            }
            ps.executeUpdate();
        }
    }

    public void clearRolledBack(List<Long> ids) throws SQLException {
        if (ids.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE yap_protect_changes SET rolled_back = ? WHERE server_id = ? AND id IN ("
                             + placeholders + ")")) {
            ps.setBoolean(1, false);
            ps.setString(2, serverId);
            for (int i = 0; i < ids.size(); i++) {
                ps.setLong(i + 3, ids.get(i));
            }
            ps.executeUpdate();
        }
    }

    public long pruneBefore(long epochMs) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM yap_protect_changes WHERE server_id = ? AND epoch_ms < ?")) {
            ps.setString(1, serverId);
            ps.setLong(2, epochMs);
            return ps.executeUpdate();
        }
    }

    public long countAll() throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM yap_protect_changes WHERE server_id = ?")) {
            ps.setString(1, serverId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private static List<ProtectChange> readAll(PreparedStatement ps) throws SQLException {
        List<ProtectChange> out = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(map(rs));
            }
        }
        return out;
    }

    private static ProtectChange map(ResultSet rs) throws SQLException {
        String actorRaw = rs.getString("actor_uuid");
        UUID actor = actorRaw == null || actorRaw.isBlank() ? null : UUID.fromString(actorRaw);
        return new ProtectChange(
                rs.getLong("id"),
                rs.getString("server_id"),
                ChangeType.valueOf(rs.getString("change_type")),
                actor,
                rs.getString("actor_name"),
                rs.getString("world"),
                rs.getInt("x"),
                rs.getInt("y"),
                rs.getInt("z"),
                rs.getString("block_before"),
                rs.getString("block_after"),
                rs.getLong("epoch_ms"),
                rs.getBoolean("rolled_back"));
    }

    /** Package-visible for unit tests (block payload length caps). */
    static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 65535 ? value : value.substring(0, 65535);
    }
}
