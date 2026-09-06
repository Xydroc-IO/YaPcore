package com.yapcore.conquest.db;

import com.yapcore.conquest.ConquestChunk;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ConquestRepository {

    private final ConquestDatabase database;

    public ConquestRepository(ConquestDatabase database) {
        this.database = database;
    }

    public Optional<ConquestChunk> find(String world, int chunkX, int chunkZ) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM yap_conquest_chunks WHERE world = ? AND chunk_x = ? AND chunk_z = ?")) {
            ps.setString(1, world);
            ps.setInt(2, chunkX);
            ps.setInt(3, chunkZ);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        }
    }

    public List<ConquestChunk> listForFaction(long factionId) throws SQLException {
        List<ConquestChunk> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM yap_conquest_chunks WHERE faction_id = ? ORDER BY claimed_at")) {
            ps.setLong(1, factionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
        }
        return out;
    }

    public int sumPowerCost(long factionId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COALESCE(SUM(power_cost), 0) FROM yap_conquest_chunks WHERE faction_id = ?")) {
            ps.setLong(1, factionId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public void insert(ConquestChunk chunk) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO yap_conquest_chunks
                       (world, chunk_x, chunk_z, faction_id, power_cost, claimed_at, frozen)
                     VALUES (?, ?, ?, ?, ?, ?, ?)
                     """)) {
            ps.setString(1, chunk.world());
            ps.setInt(2, chunk.chunkX());
            ps.setInt(3, chunk.chunkZ());
            ps.setLong(4, chunk.factionId());
            ps.setInt(5, chunk.powerCost());
            ps.setTimestamp(6, Timestamp.from(chunk.claimedAt()));
            ps.setBoolean(7, chunk.frozen());
            ps.executeUpdate();
        }
    }

    public void delete(String world, int chunkX, int chunkZ) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM yap_conquest_chunks WHERE world = ? AND chunk_x = ? AND chunk_z = ?")) {
            ps.setString(1, world);
            ps.setInt(2, chunkX);
            ps.setInt(3, chunkZ);
            ps.executeUpdate();
        }
    }

    public void transfer(
            String world, int chunkX, int chunkZ, long newFactionId, int powerCost, Instant claimedAt)
            throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     UPDATE yap_conquest_chunks
                     SET faction_id = ?, power_cost = ?, claimed_at = ?, frozen = 0
                     WHERE world = ? AND chunk_x = ? AND chunk_z = ?
                     """)) {
            ps.setLong(1, newFactionId);
            ps.setInt(2, powerCost);
            ps.setTimestamp(3, Timestamp.from(claimedAt));
            ps.setString(4, world);
            ps.setInt(5, chunkX);
            ps.setInt(6, chunkZ);
            if (ps.executeUpdate() == 0) {
                throw new SQLException("Chunk missing for transfer");
            }
        }
    }

    private static ConquestChunk map(ResultSet rs) throws SQLException {
        Timestamp claimed = rs.getTimestamp("claimed_at");
        return new ConquestChunk(
                rs.getString("world"),
                rs.getInt("chunk_x"),
                rs.getInt("chunk_z"),
                rs.getLong("faction_id"),
                rs.getInt("power_cost"),
                claimed == null ? Instant.EPOCH : claimed.toInstant(),
                rs.getBoolean("frozen"));
    }
}
