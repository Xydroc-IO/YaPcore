package com.yapcore.playerdata.db;

import org.bukkit.Material;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static java.util.Map.entry;

public final class ShopRepository {
    public record Shop(long id, UUID owner, String serverId, String world, int x, int y, int z,
                       Material material, int amount, double price) {
    }

    private final Database database;

    public ShopRepository(Database database) {
        this.database = database;
    }

    public Optional<Shop> findAt(String serverId, String world, int x, int y, int z) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT id, owner_uuid, server_id, world, x, y, z, material, amount, price
                     FROM shops WHERE server_id = ? AND world = ? AND x = ? AND y = ? AND z = ?
                     """)) {
            ps.setString(1, serverId);
            ps.setString(2, world);
            ps.setInt(3, x);
            ps.setInt(4, y);
            ps.setInt(5, z);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        }
    }

    public void upsert(Shop shop) throws SQLException {
        String sql = database.dialect().upsert(
                "shops",
                List.of("server_id", "world", "x", "y", "z"),
                List.of("owner_uuid", "server_id", "world", "x", "y", "z", "material", "amount", "price"),
                Map.ofEntries(
                        entry("owner_uuid", "EXCLUDED.owner_uuid"),
                        entry("material", "EXCLUDED.material"),
                        entry("amount", "EXCLUDED.amount"),
                        entry("price", "EXCLUDED.price")));
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, shop.owner().toString());
            ps.setString(2, shop.serverId());
            ps.setString(3, shop.world());
            ps.setInt(4, shop.x());
            ps.setInt(5, shop.y());
            ps.setInt(6, shop.z());
            ps.setString(7, shop.material().name());
            ps.setInt(8, shop.amount());
            ps.setDouble(9, shop.price());
            ps.executeUpdate();
        }
    }

    public boolean delete(String serverId, String world, int x, int y, int z) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM shops WHERE server_id = ? AND world = ? AND x = ? AND y = ? AND z = ?")) {
            ps.setString(1, serverId);
            ps.setString(2, world);
            ps.setInt(3, x);
            ps.setInt(4, y);
            ps.setInt(5, z);
            return ps.executeUpdate() > 0;
        }
    }

    private static Shop map(ResultSet rs) throws SQLException {
        Material mat = Material.matchMaterial(rs.getString("material"));
        if (mat == null) {
            mat = Material.STONE;
        }
        return new Shop(
                rs.getLong("id"),
                UUID.fromString(rs.getString("owner_uuid")),
                rs.getString("server_id"),
                rs.getString("world"),
                rs.getInt("x"),
                rs.getInt("y"),
                rs.getInt("z"),
                mat,
                rs.getInt("amount"),
                rs.getDouble("price"));
    }
}
