package com.yapcore.playerdata.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class KitRepository {
    private final Database database;

    public KitRepository(Database database) {
        this.database = database;
    }

    public Optional<Instant> lastClaim(UUID uuid, String kit) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT claimed_at FROM kit_cooldowns WHERE uuid = ? AND kit = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, kit);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Timestamp ts = rs.getTimestamp("claimed_at");
                return ts == null ? Optional.empty() : Optional.of(ts.toInstant());
            }
        }
    }

    public void markClaimed(UUID uuid, String kit) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO kit_cooldowns (uuid, kit, claimed_at) VALUES (?, ?, ?)
                     ON DUPLICATE KEY UPDATE claimed_at = VALUES(claimed_at)
                     """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, kit);
            ps.setTimestamp(3, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        }
    }
}
