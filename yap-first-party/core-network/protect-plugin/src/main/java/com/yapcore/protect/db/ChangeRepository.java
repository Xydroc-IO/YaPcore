package com.yapcore.protect.db;

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

    private final ProtectDatabase database;

    public ChangeRepository(ProtectDatabase database) {
        this.database = database;
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
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT id, change_type, actor_uuid, actor_name, world, x, y, z,
                            block_before, block_after, epoch_ms, rolled_back
                     FROM yap_protect_changes
                     WHERE actor_uuid = ? AND epoch_ms BETWEEN ? AND ?
                     ORDER BY epoch_ms DESC LIMIT ?
                     """)) {
            ps.setString(1, actorUuid.toString());
            ps.setLong(2, fromMs);
            ps.setLong(3, toMs);
            ps.setInt(4, Math.max(1, Math.min(limit, 500)));
            return readAll(ps);
        }
    }

    public List<ProtectChange> lookupBlock(String world, int x, int y, int z,
                                           long fromMs, long toMs, int limit) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT id, change_type, actor_uuid, actor_name, world, x, y, z,
                            block_before, block_after, epoch_ms, rolled_back
                     FROM yap_protect_changes
                     WHERE world = ? AND x = ? AND y = ? AND z = ?
                       AND epoch_ms BETWEEN ? AND ?
                     ORDER BY epoch_ms DESC LIMIT ?
                     """)) {
            ps.setString(1, world);
            ps.setInt(2, x);
            ps.setInt(3, y);
            ps.setInt(4, z);
            ps.setLong(5, fromMs);
            ps.setLong(6, toMs);
            ps.setInt(7, Math.max(1, Math.min(limit, 500)));
            return readAll(ps);
        }
    }

    public List<ProtectChange> lookupRadius(String world, int cx, int cy, int cz, int radius,
                                           long fromMs, long toMs, int limit) throws SQLException {
        int r = Math.max(0, radius);
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT id, change_type, actor_uuid, actor_name, world, x, y, z,
                            block_before, block_after, epoch_ms, rolled_back
                     FROM yap_protect_changes
                     WHERE world = ?
                       AND x BETWEEN ? AND ?
                       AND y BETWEEN ? AND ?
                       AND z BETWEEN ? AND ?
                       AND epoch_ms BETWEEN ? AND ?
                     ORDER BY epoch_ms DESC LIMIT ?
                     """)) {
            ps.setString(1, world);
            ps.setInt(2, cx - r);
            ps.setInt(3, cx + r);
            ps.setInt(4, cy - r);
            ps.setInt(5, cy + r);
            ps.setInt(6, cz - r);
            ps.setInt(7, cz + r);
            ps.setLong(8, fromMs);
            ps.setLong(9, toMs);
            ps.setInt(10, Math.max(1, Math.min(limit, 500)));
            return readAll(ps);
        }
    }

    public List<ProtectChange> lookupTimeRange(String world, long fromMs, long toMs, int limit) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT id, change_type, actor_uuid, actor_name, world, x, y, z,
                            block_before, block_after, epoch_ms, rolled_back
                     FROM yap_protect_changes
                     WHERE world = ? AND epoch_ms BETWEEN ? AND ?
                     ORDER BY epoch_ms DESC LIMIT ?
                     """)) {
            ps.setString(1, world);
            ps.setLong(2, fromMs);
            ps.setLong(3, toMs);
            ps.setInt(4, Math.max(1, Math.min(limit, 500)));
            return readAll(ps);
        }
    }

    public List<ProtectChange> lookupActorInRadius(UUID actorUuid, String world, int cx, int cy, int cz,
                                                   int radius, long fromMs, long toMs, int limit) throws SQLException {
        int r = Math.max(0, radius);
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT id, change_type, actor_uuid, actor_name, world, x, y, z,
                            block_before, block_after, epoch_ms, rolled_back
                     FROM yap_protect_changes
                     WHERE actor_uuid = ? AND world = ?
                       AND x BETWEEN ? AND ?
                       AND y BETWEEN ? AND ?
                       AND z BETWEEN ? AND ?
                       AND epoch_ms BETWEEN ? AND ?
                     ORDER BY epoch_ms DESC LIMIT ?
                     """)) {
            ps.setString(1, actorUuid.toString());
            ps.setString(2, world);
            ps.setInt(3, cx - r);
            ps.setInt(4, cx + r);
            ps.setInt(5, cy - r);
            ps.setInt(6, cy + r);
            ps.setInt(7, cz - r);
            ps.setInt(8, cz + r);
            ps.setLong(9, fromMs);
            ps.setLong(10, toMs);
            ps.setInt(11, Math.max(1, Math.min(limit, 500)));
            return readAll(ps);
        }
    }

    public List<ProtectChange> fetchByIds(List<Long> ids) throws SQLException {
        if (ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT id, change_type, actor_uuid, actor_name, world, x, y, z,
                            block_before, block_after, epoch_ms, rolled_back
                     FROM yap_protect_changes
                     WHERE id IN (""" + placeholders + ") ORDER BY epoch_ms ASC")) {
            for (int i = 0; i < ids.size(); i++) {
                ps.setLong(i + 1, ids.get(i));
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
                     "UPDATE yap_protect_changes SET rolled_back = ? WHERE id IN (" + placeholders + ")")) {
            ps.setBoolean(1, true);
            for (int i = 0; i < ids.size(); i++) {
                ps.setLong(i + 2, ids.get(i));
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
                     "UPDATE yap_protect_changes SET rolled_back = ? WHERE id IN (" + placeholders + ")")) {
            ps.setBoolean(1, false);
            for (int i = 0; i < ids.size(); i++) {
                ps.setLong(i + 2, ids.get(i));
            }
            ps.executeUpdate();
        }
    }

    public long pruneBefore(long epochMs) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM yap_protect_changes WHERE epoch_ms < ?")) {
            ps.setLong(1, epochMs);
            return ps.executeUpdate();
        }
    }

    public long countAll() throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM yap_protect_changes");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
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

    private static String truncateBlock(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 255 ? value : value.substring(0, 255);
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 65535 ? value : value.substring(0, 65535);
    }
}
