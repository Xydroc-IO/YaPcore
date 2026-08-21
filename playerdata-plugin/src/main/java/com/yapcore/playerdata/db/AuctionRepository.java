package com.yapcore.playerdata.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class AuctionRepository {
    public record Listing(long id, UUID seller, String sellerName, double price,
                          byte[] itemBlob, Instant created, Instant expires) {
    }

    private final Database database;

    public AuctionRepository(Database database) {
        this.database = database;
    }

    public long create(UUID seller, String sellerName, double price, byte[] item, Instant expires)
            throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO auctions (seller_uuid, seller_name, price, item_blob, expires_at)
                     VALUES (?, ?, ?, ?, ?)
                     """, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, seller.toString());
            ps.setString(2, sellerName.length() > 16 ? sellerName.substring(0, 16) : sellerName);
            ps.setDouble(3, price);
            ps.setBytes(4, item);
            ps.setTimestamp(5, Timestamp.from(expires));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("No auction id");
    }

    public List<Listing> listActive(int limit) throws SQLException {
        List<Listing> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT id, seller_uuid, seller_name, price, item_blob, created_at, expires_at
                     FROM auctions WHERE expires_at > ? ORDER BY id DESC LIMIT ?
                     """)) {
            ps.setTimestamp(1, Timestamp.from(Instant.now()));
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
        }
        return out;
    }

    public Optional<Listing> get(long id) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT id, seller_uuid, seller_name, price, item_blob, created_at, expires_at
                     FROM auctions WHERE id = ?
                     """)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        }
    }

    public boolean delete(long id) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM auctions WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    private static Listing map(ResultSet rs) throws SQLException {
        return new Listing(
                rs.getLong("id"),
                UUID.fromString(rs.getString("seller_uuid")),
                rs.getString("seller_name"),
                rs.getDouble("price"),
                rs.getBytes("item_blob"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant());
    }
}
