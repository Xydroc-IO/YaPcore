package com.yapcore.factions.db;

import com.yapcore.factions.FactionWarp;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class FactionWarpQueries {

    private final FactionDatabase database;

    FactionWarpQueries(FactionDatabase database) {
        this.database = database;
    }

    void upsert(FactionWarp warp) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(database.dialect().upsert(
                     "yap_faction_warps",
                     List.of("faction_id", "name"),
                     List.of("faction_id", "name", "world", "x", "y", "z", "yaw", "pitch"),
                     java.util.Map.of(
                             "world", "EXCLUDED.world",
                             "x", "EXCLUDED.x",
                             "y", "EXCLUDED.y",
                             "z", "EXCLUDED.z",
                             "yaw", "EXCLUDED.yaw",
                             "pitch", "EXCLUDED.pitch")))) {
            ps.setLong(1, warp.factionId());
            ps.setString(2, warp.name());
            ps.setString(3, warp.world());
            ps.setDouble(4, warp.x());
            ps.setDouble(5, warp.y());
            ps.setDouble(6, warp.z());
            ps.setFloat(7, warp.yaw());
            ps.setFloat(8, warp.pitch());
            ps.executeUpdate();
        }
    }

    void delete(long factionId, String name) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM yap_faction_warps WHERE faction_id = ? AND name = ?")) {
            ps.setLong(1, factionId);
            ps.setString(2, name);
            ps.executeUpdate();
        }
    }

    void deleteAll(long factionId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM yap_faction_warps WHERE faction_id = ?")) {
            ps.setLong(1, factionId);
            ps.executeUpdate();
        }
    }

    Optional<FactionWarp> get(long factionId, String name) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT faction_id, name, world, x, y, z, yaw, pitch FROM yap_faction_warps "
                             + "WHERE faction_id = ? AND name = ?")) {
            ps.setLong(1, factionId);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    List<FactionWarp> list(long factionId) throws SQLException {
        List<FactionWarp> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT faction_id, name, world, x, y, z, yaw, pitch FROM yap_faction_warps "
                             + "WHERE faction_id = ? ORDER BY name")) {
            ps.setLong(1, factionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
        }
        return out;
    }

    int count(long factionId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM yap_faction_warps WHERE faction_id = ?")) {
            ps.setLong(1, factionId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public static String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private static FactionWarp map(ResultSet rs) throws SQLException {
        return new FactionWarp(
                rs.getLong("faction_id"),
                rs.getString("name"),
                rs.getString("world"),
                rs.getDouble("x"),
                rs.getDouble("y"),
                rs.getDouble("z"),
                rs.getFloat("yaw"),
                rs.getFloat("pitch"));
    }
}
