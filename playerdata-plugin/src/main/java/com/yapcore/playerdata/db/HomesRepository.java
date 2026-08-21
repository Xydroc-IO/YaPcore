package com.yapcore.playerdata.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class HomesRepository {
    private final Database database;

    public HomesRepository(Database database) {
        this.database = database;
    }

    public List<LocationRow> list(UUID uuid) throws SQLException {
        List<LocationRow> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT name, server_id, world, x, y, z, yaw, pitch FROM homes WHERE uuid = ? ORDER BY name")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs, uuid));
                }
            }
        }
        return out;
    }

    public Optional<LocationRow> get(UUID uuid, String name) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT name, server_id, world, x, y, z, yaw, pitch FROM homes WHERE uuid = ? AND name = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name.toLowerCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs, uuid));
            }
        }
    }

    public int count(UUID uuid) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM homes WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public void upsert(UUID uuid, LocationRow loc) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO homes (uuid, name, server_id, world, x, y, z, yaw, pitch)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON DUPLICATE KEY UPDATE server_id=VALUES(server_id), world=VALUES(world),
                       x=VALUES(x), y=VALUES(y), z=VALUES(z), yaw=VALUES(yaw), pitch=VALUES(pitch)
                     """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, loc.name().toLowerCase(Locale.ROOT));
            ps.setString(3, loc.serverId());
            ps.setString(4, loc.world());
            ps.setDouble(5, loc.x());
            ps.setDouble(6, loc.y());
            ps.setDouble(7, loc.z());
            ps.setFloat(8, loc.yaw());
            ps.setFloat(9, loc.pitch());
            ps.executeUpdate();
        }
    }

    public boolean delete(UUID uuid, String name) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM homes WHERE uuid = ? AND name = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name.toLowerCase(Locale.ROOT));
            return ps.executeUpdate() > 0;
        }
    }

    private static LocationRow map(ResultSet rs, UUID uuid) throws SQLException {
        return new LocationRow(
                rs.getString("name"),
                rs.getString("server_id"),
                rs.getString("world"),
                rs.getDouble("x"),
                rs.getDouble("y"),
                rs.getDouble("z"),
                rs.getFloat("yaw"),
                rs.getFloat("pitch"),
                uuid);
    }
}
