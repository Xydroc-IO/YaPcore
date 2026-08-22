package com.yapcore.playerdata.db;

import org.bukkit.Material;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class NpcTraderRepository {

    public record Trader(long id, String serverId, String world, double x, double y, double z,
                         float yaw, String name, UUID entityUuid) {
    }

    public record Offer(long id, long traderId, String mode, Material material, int amount,
                        double price, int stock) {
    }

    private final Database database;

    public NpcTraderRepository(Database database) {
        this.database = database;
    }

    public List<Trader> listForServer(String serverId) throws SQLException {
        List<Trader> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT id, server_id, world, x, y, z, yaw, name, entity_uuid
                     FROM npc_traders WHERE server_id = ?
                     """)) {
            ps.setString(1, serverId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapTrader(rs));
                }
            }
        }
        return out;
    }

    public Optional<Trader> get(long id) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT id, server_id, world, x, y, z, yaw, name, entity_uuid
                     FROM npc_traders WHERE id = ?
                     """)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapTrader(rs));
            }
        }
    }

    public long create(Trader t) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO npc_traders (server_id, world, x, y, z, yaw, name, entity_uuid)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                     """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, t.serverId());
            ps.setString(2, t.world());
            ps.setDouble(3, t.x());
            ps.setDouble(4, t.y());
            ps.setDouble(5, t.z());
            ps.setFloat(6, t.yaw());
            ps.setString(7, t.name());
            ps.setString(8, t.entityUuid() != null ? t.entityUuid().toString() : null);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("No trader id");
    }

    public void setEntityUuid(long id, UUID entityUuid) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE npc_traders SET entity_uuid = ? WHERE id = ?")) {
            ps.setString(1, entityUuid != null ? entityUuid.toString() : null);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    public boolean delete(long id) throws SQLException {
        try (Connection c = database.connection()) {
            try (PreparedStatement o = c.prepareStatement("DELETE FROM npc_offers WHERE trader_id = ?")) {
                o.setLong(1, id);
                o.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM npc_traders WHERE id = ?")) {
                ps.setLong(1, id);
                return ps.executeUpdate() > 0;
            }
        }
    }

    public List<Offer> offers(long traderId) throws SQLException {
        List<Offer> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT id, trader_id, mode, material, amount, price, stock
                     FROM npc_offers WHERE trader_id = ? ORDER BY id
                     """)) {
            ps.setLong(1, traderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Material mat = Material.matchMaterial(rs.getString("material"));
                    if (mat == null) {
                        mat = Material.STONE;
                    }
                    out.add(new Offer(
                            rs.getLong("id"),
                            rs.getLong("trader_id"),
                            rs.getString("mode"),
                            mat,
                            rs.getInt("amount"),
                            rs.getDouble("price"),
                            rs.getInt("stock")));
                }
            }
        }
        return out;
    }

    public long addOffer(long traderId, String mode, Material material, int amount, double price, int stock)
            throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO npc_offers (trader_id, mode, material, amount, price, stock)
                     VALUES (?, ?, ?, ?, ?, ?)
                     """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, traderId);
            ps.setString(2, mode.toUpperCase());
            ps.setString(3, material.name());
            ps.setInt(4, amount);
            ps.setDouble(5, price);
            ps.setInt(6, stock);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("No offer id");
    }

    public void setStock(long offerId, int stock) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("UPDATE npc_offers SET stock = ? WHERE id = ?")) {
            ps.setInt(1, stock);
            ps.setLong(2, offerId);
            ps.executeUpdate();
        }
    }

    public boolean deleteOffer(long offerId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM npc_offers WHERE id = ?")) {
            ps.setLong(1, offerId);
            return ps.executeUpdate() > 0;
        }
    }

    private static Trader mapTrader(ResultSet rs) throws SQLException {
        String eu = rs.getString("entity_uuid");
        return new Trader(
                rs.getLong("id"),
                rs.getString("server_id"),
                rs.getString("world"),
                rs.getDouble("x"),
                rs.getDouble("y"),
                rs.getDouble("z"),
                rs.getFloat("yaw"),
                rs.getString("name"),
                eu == null ? null : UUID.fromString(eu));
    }
}
