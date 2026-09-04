package com.yapcore.playerdata.db;

import com.yapcore.playerdata.sync.ItemSerializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class BackpackRepository {

    public record Owner(UUID uuid, String name) {
    }

    private final Database database;

    public BackpackRepository(Database database) {
        this.database = database;
    }

    public byte[] loadOrEmpty(UUID uuid, String profile, int page, int slots) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT contents FROM player_backpack_pages
                     WHERE uuid = ? AND profile = ? AND page = ?
                     """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, profile);
            ps.setInt(3, page);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    byte[] blob = rs.getBytes("contents");
                    if (blob != null && blob.length > 0) {
                        return blob;
                    }
                }
            }
        }
        return ItemSerializer.empty(slots);
    }

    public void save(UUID uuid, String profile, int page, byte[] contents) throws SQLException {
        byte[] blob = contents != null ? contents : ItemSerializer.empty(45);
        String sql = database.dialect().upsert(
                "player_backpack_pages",
                List.of("uuid", "profile", "page"),
                List.of("uuid", "profile", "page", "contents"),
                Map.of("contents", "EXCLUDED.contents"));
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, profile);
            ps.setInt(3, page);
            ps.setBytes(4, blob);
            ps.executeUpdate();
        }
    }

    public Optional<Owner> findOwnerByName(String name) throws SQLException {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT uuid, name FROM players WHERE name = ? LIMIT 1
                     """)) {
            String trimmed = name.length() > 16 ? name.substring(0, 16) : name;
            ps.setString(1, trimmed);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new Owner(UUID.fromString(rs.getString("uuid")), rs.getString("name")));
                }
            }
        }
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT uuid, name FROM players WHERE LOWER(name) = ? LIMIT 1
                     """)) {
            ps.setString(1, name.toLowerCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Owner(UUID.fromString(rs.getString("uuid")), rs.getString("name")));
            }
        }
    }
}
