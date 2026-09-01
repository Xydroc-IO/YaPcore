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

    /** Queue a one-shot store/admin grant (delivered on any backend when the player is online). */
    public long enqueueGrant(UUID uuid, String kit) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO kit_grants (uuid, kit) VALUES (?, ?)",
                     PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, kit);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        return -1L;
    }

    public List<PendingGrant> pendingGrants(UUID uuid) throws SQLException {
        List<PendingGrant> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT id, kit FROM kit_grants
                     WHERE uuid = ? AND delivered_at IS NULL
                     ORDER BY id ASC
                     """)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new PendingGrant(rs.getLong("id"), rs.getString("kit")));
                }
            }
        }
        return out;
    }

    public void markGrantDelivered(long id) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE kit_grants SET delivered_at = ? WHERE id = ? AND delivered_at IS NULL")) {
            ps.setTimestamp(1, Timestamp.from(Instant.now()));
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    public record PendingGrant(long id, String kit) {
    }
}
