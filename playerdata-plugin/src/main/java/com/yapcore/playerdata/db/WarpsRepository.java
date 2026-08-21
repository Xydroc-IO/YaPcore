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

public final class WarpsRepository {
    private final Database database;

    public WarpsRepository(Database database) {
        this.database = database;
    }

    public List<LocationRow> list() throws SQLException {
        List<LocationRow> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT name, server_id, world, x, y, z, yaw, pitch, created_by FROM warps ORDER BY name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UUID owner = null;
                String cb = rs.getString("created_by");
                if (cb != null) {
                    owner = UUID.fromString(cb);
                }
                out.add(new LocationRow(
                        rs.getString("name"),
                        rs.getString("server_id"),
                        rs.getString("world"),
                        rs.getDouble("x"),
                        rs.getDouble("y"),
                        rs.getDouble("z"),
                        rs.getFloat("yaw"),
                        rs.getFloat("pitch"),
                        owner));
            }
        }
        return out;
    }

    public Optional<LocationRow> get(String name) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT name, server_id, world, x, y, z, yaw, pitch, created_by FROM warps WHERE name = ?")) {
            ps.setString(1, name.toLowerCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                UUID owner = null;
                String cb = rs.getString("created_by");
                if (cb != null) {
                    owner = UUID.fromString(cb);
                }
                return Optional.of(new LocationRow(
                        rs.getString("name"),
                        rs.getString("server_id"),
                        rs.getString("world"),
                        rs.getDouble("x"),
                        rs.getDouble("y"),
                        rs.getDouble("z"),
                        rs.getFloat("yaw"),
                        rs.getFloat("pitch"),
                        owner));
            }
        }
    }

    public void upsert(LocationRow loc) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO warps (name, server_id, world, x, y, z, yaw, pitch, created_by)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON DUPLICATE KEY UPDATE server_id=VALUES(server_id), world=VALUES(world),
                       x=VALUES(x), y=VALUES(y), z=VALUES(z), yaw=VALUES(yaw), pitch=VALUES(pitch)
                     """)) {
            ps.setString(1, loc.name().toLowerCase(Locale.ROOT));
            ps.setString(2, loc.serverId());
            ps.setString(3, loc.world());
            ps.setDouble(4, loc.x());
            ps.setDouble(5, loc.y());
            ps.setDouble(6, loc.z());
            ps.setFloat(7, loc.yaw());
            ps.setFloat(8, loc.pitch());
            if (loc.owner() != null) {
                ps.setString(9, loc.owner().toString());
            } else {
                ps.setString(9, null);
            }
            ps.executeUpdate();
        }
    }

    public boolean delete(String name) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM warps WHERE name = ?")) {
            ps.setString(1, name.toLowerCase(Locale.ROOT));
            return ps.executeUpdate() > 0;
        }
    }
}
