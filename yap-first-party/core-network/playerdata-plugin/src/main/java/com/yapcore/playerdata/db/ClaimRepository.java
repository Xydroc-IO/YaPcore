package com.yapcore.playerdata.db;

import com.yapcore.playerdata.claims.Claim;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ClaimRepository {

    public enum TrustLevel {
        ACCESS, BUILD, MANAGE;

        public static TrustLevel parse(String s) {
            return TrustLevel.valueOf(s.toUpperCase(Locale.ROOT));
        }

        public boolean atLeast(TrustLevel needed) {
            return ordinal() >= needed.ordinal();
        }
    }

    private static final String COLS =
            "id, owner_uuid, server_id, world, min_x, max_x, min_z, max_z, name, parent_id, tax_due, tax_frozen";

    private final Database database;

    public ClaimRepository(Database database) {
        this.database = database;
    }

    public List<Claim> listForServer(String serverId) throws SQLException {
        List<Claim> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT " + COLS + " FROM claims WHERE server_id = ?")) {
            ps.setString(1, serverId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
        }
        return out;
    }

    public List<Claim> listOwned(UUID owner) throws SQLException {
        List<Claim> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT " + COLS + " FROM claims WHERE owner_uuid = ? ORDER BY parent_id IS NOT NULL, id")) {
            ps.setString(1, owner.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
        }
        return out;
    }

    public List<Claim> listChildren(long parentId) throws SQLException {
        List<Claim> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT " + COLS + " FROM claims WHERE parent_id = ?")) {
            ps.setLong(1, parentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
        }
        return out;
    }

    public Optional<Claim> get(long id) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("SELECT " + COLS + " FROM claims WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        }
    }

    public long create(Claim claim) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO claims
                     (owner_uuid, server_id, world, min_x, max_x, min_z, max_z, name, parent_id, tax_due, tax_frozen)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, claim.owner().toString());
            ps.setString(2, claim.serverId());
            ps.setString(3, claim.world());
            ps.setInt(4, claim.minX());
            ps.setInt(5, claim.maxX());
            ps.setInt(6, claim.minZ());
            ps.setInt(7, claim.maxZ());
            ps.setString(8, claim.name());
            if (claim.parentId() == null) {
                ps.setNull(9, Types.BIGINT);
            } else {
                ps.setLong(9, claim.parentId());
            }
            ps.setDouble(10, claim.taxDue());
            ps.setBoolean(11, claim.taxFrozen());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("No claim id");
    }

    public boolean delete(long id) throws SQLException {
        try (Connection c = database.connection()) {
            // children first
            try (PreparedStatement kids = c.prepareStatement("SELECT id FROM claims WHERE parent_id = ?")) {
                kids.setLong(1, id);
                try (ResultSet rs = kids.executeQuery()) {
                    while (rs.next()) {
                        deleteOne(c, rs.getLong(1));
                    }
                }
            }
            return deleteOne(c, id);
        }
    }

    private boolean deleteOne(Connection c, long id) throws SQLException {
        try (PreparedStatement t = c.prepareStatement("DELETE FROM claim_trust WHERE claim_id = ?")) {
            t.setLong(1, id);
            t.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM claims WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public void setName(long id, String name) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("UPDATE claims SET name = ? WHERE id = ?")) {
            ps.setString(1, name);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    public void setTax(long id, double due, boolean frozen) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE claims SET tax_due = ?, tax_frozen = ? WHERE id = ?")) {
            ps.setDouble(1, due);
            ps.setBoolean(2, frozen);
            ps.setLong(3, id);
            ps.executeUpdate();
        }
    }

    public List<Claim> listTopLevelForTax(String serverId) throws SQLException {
        List<Claim> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT " + COLS + " FROM claims WHERE server_id = ? AND parent_id IS NULL")) {
            ps.setString(1, serverId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
        }
        return out;
    }

    public Map<UUID, TrustLevel> trustMap(long claimId) throws SQLException {
        Map<UUID, TrustLevel> out = new HashMap<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT player_uuid, level FROM claim_trust WHERE claim_id = ?")) {
            ps.setLong(1, claimId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(UUID.fromString(rs.getString("player_uuid")),
                            TrustLevel.parse(rs.getString("level")));
                }
            }
        }
        return out;
    }

    public void setTrust(long claimId, UUID player, TrustLevel level) throws SQLException {
        String sql = database.dialect().upsert(
                "claim_trust",
                List.of("claim_id", "player_uuid"),
                List.of("claim_id", "player_uuid", "level"),
                Map.of("level", "EXCLUDED.level"));
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, claimId);
            ps.setString(2, player.toString());
            ps.setString(3, level.name());
            ps.executeUpdate();
        }
    }

    public void removeTrust(long claimId, UUID player) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM claim_trust WHERE claim_id = ? AND player_uuid = ?")) {
            ps.setLong(1, claimId);
            ps.setString(2, player.toString());
            ps.executeUpdate();
        }
    }

    public int getBlocks(UUID uuid, int defaultBlocks) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT blocks FROM claim_balances WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("blocks");
                }
            }
        }
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     database.dialect().insertIgnore("claim_balances", List.of("uuid", "blocks")))) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, defaultBlocks);
            ps.executeUpdate();
        }
        return defaultBlocks;
    }

    public void setBlocks(UUID uuid, int blocks) throws SQLException {
        String sql = database.dialect().upsert(
                "claim_balances",
                List.of("uuid"),
                List.of("uuid", "blocks"),
                Map.of("blocks", "EXCLUDED.blocks"));
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, Math.max(0, blocks));
            ps.executeUpdate();
        }
    }

    private static Claim map(ResultSet rs) throws SQLException {
        long parent = rs.getLong("parent_id");
        Long parentId = rs.wasNull() ? null : parent;
        return new Claim(
                rs.getLong("id"),
                UUID.fromString(rs.getString("owner_uuid")),
                rs.getString("server_id"),
                rs.getString("world"),
                rs.getInt("min_x"),
                rs.getInt("max_x"),
                rs.getInt("min_z"),
                rs.getInt("max_z"),
                rs.getString("name"),
                parentId,
                rs.getDouble("tax_due"),
                rs.getBoolean("tax_frozen"));
    }
}
