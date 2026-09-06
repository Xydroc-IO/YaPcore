package com.yapcore.conquest.db;

import com.yapcore.conquest.ConquestZoneType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ConquestZoneRepository {

    private final ConquestDatabase database;

    public ConquestZoneRepository(ConquestDatabase database) {
        this.database = database;
    }

    public Optional<ConquestZoneType> find(String world, int chunkX, int chunkZ) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT zone_type FROM yap_conquest_zones WHERE world = ? AND chunk_x = ? AND chunk_z = ?")) {
            ps.setString(1, world);
            ps.setInt(2, chunkX);
            ps.setInt(3, chunkZ);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return ConquestZoneType.parse(rs.getString("zone_type"));
            }
        }
    }

    public void upsert(String world, int chunkX, int chunkZ, ConquestZoneType type) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(database.dialect().upsert(
                     "yap_conquest_zones",
                     List.of("world", "chunk_x", "chunk_z"),
                     List.of("world", "chunk_x", "chunk_z", "zone_type"),
                     Map.of("zone_type", "EXCLUDED.zone_type")))) {
            ps.setString(1, world);
            ps.setInt(2, chunkX);
            ps.setInt(3, chunkZ);
            ps.setString(4, type.name());
            ps.executeUpdate();
        }
    }

    public void delete(String world, int chunkX, int chunkZ) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM yap_conquest_zones WHERE world = ? AND chunk_x = ? AND chunk_z = ?")) {
            ps.setString(1, world);
            ps.setInt(2, chunkX);
            ps.setInt(3, chunkZ);
            ps.executeUpdate();
        }
    }
}
