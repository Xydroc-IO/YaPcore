package com.yapcore.regions.db;

import com.yapcore.regions.AdminRegion;
import com.yapcore.regions.FlagValue;
import com.yapcore.regions.RegionFlag;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AdminRegionRepository {

    private final RegionsDatabase database;

    public AdminRegionRepository(RegionsDatabase database) {
        this.database = database;
    }

    public List<AdminRegion> loadForServer(String serverId) throws SQLException {
        List<AdminRegion> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT id, server_id, world, min_x, max_x, min_y, max_y, min_z, max_z, name
                     FROM yap_admin_regions WHERE server_id = ?
                     ORDER BY name
                     """)) {
            ps.setString(1, serverId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    out.add(new AdminRegion(
                            id,
                            rs.getString("server_id"),
                            rs.getString("world"),
                            rs.getInt("min_x"),
                            rs.getInt("max_x"),
                            rs.getInt("min_y"),
                            rs.getInt("max_y"),
                            rs.getInt("min_z"),
                            rs.getInt("max_z"),
                            rs.getString("name"),
                            loadFlags(id)));
                }
            }
        }
        return out;
    }

    public Optional<AdminRegion> findByName(String serverId, String name) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT id, server_id, world, min_x, max_x, min_y, max_y, min_z, max_z, name
                     FROM yap_admin_regions WHERE server_id = ? AND name = ?
                     """)) {
            ps.setString(1, serverId);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                long id = rs.getLong("id");
                return Optional.of(new AdminRegion(
                        id,
                        rs.getString("server_id"),
                        rs.getString("world"),
                        rs.getInt("min_x"),
                        rs.getInt("max_x"),
                        rs.getInt("min_y"),
                        rs.getInt("max_y"),
                        rs.getInt("min_z"),
                        rs.getInt("max_z"),
                        rs.getString("name"),
                        loadFlags(id)));
            }
        }
    }

    public long create(String serverId, String name, String world,
                       int minX, int maxX, int minY, int maxY, int minZ, int maxZ) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO yap_admin_regions
                     (server_id, name, world, min_x, max_x, min_y, max_y, min_z, max_z)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                     """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, serverId);
            ps.setString(2, name);
            ps.setString(3, world);
            ps.setInt(4, minX);
            ps.setInt(5, maxX);
            ps.setInt(6, minY);
            ps.setInt(7, maxY);
            ps.setInt(8, minZ);
            ps.setInt(9, maxZ);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("No generated key for admin region");
    }

    public void updateBounds(long regionId, String world,
                             int minX, int maxX, int minY, int maxY, int minZ, int maxZ) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     UPDATE yap_admin_regions
                     SET world = ?, min_x = ?, max_x = ?, min_y = ?, max_y = ?, min_z = ?, max_z = ?
                     WHERE id = ?
                     """)) {
            ps.setString(1, world);
            ps.setInt(2, minX);
            ps.setInt(3, maxX);
            ps.setInt(4, minY);
            ps.setInt(5, maxY);
            ps.setInt(6, minZ);
            ps.setInt(7, maxZ);
            ps.setLong(8, regionId);
            if (ps.executeUpdate() == 0) {
                throw new SQLException("Region id not found: " + regionId);
            }
        }
    }

    public void delete(long regionId) throws SQLException {
        try (Connection c = database.connection()) {
            try (PreparedStatement flags = c.prepareStatement(
                    "DELETE FROM yap_admin_region_flags WHERE region_id = ?")) {
                flags.setLong(1, regionId);
                flags.executeUpdate();
            }
            try (PreparedStatement region = c.prepareStatement(
                    "DELETE FROM yap_admin_regions WHERE id = ?")) {
                region.setLong(1, regionId);
                if (region.executeUpdate() == 0) {
                    throw new SQLException("Region id not found: " + regionId);
                }
            }
        }
    }

    public void setFlag(long regionId, RegionFlag flag, FlagValue value) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO yap_admin_region_flags (region_id, flag_name, flag_value)
                     VALUES (?, ?, ?)
                     ON DUPLICATE KEY UPDATE flag_value = VALUES(flag_value)
                     """)) {
            ps.setLong(1, regionId);
            ps.setString(2, flag.name());
            ps.setString(3, value.name());
            ps.executeUpdate();
        }
    }

    private Map<RegionFlag, FlagValue> loadFlags(long regionId) throws SQLException {
        Map<RegionFlag, FlagValue> out = new EnumMap<>(RegionFlag.class);
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT flag_name, flag_value FROM yap_admin_region_flags WHERE region_id = ?
                     """)) {
            ps.setLong(1, regionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    var flagOpt = RegionFlag.parse(rs.getString("flag_name"));
                    if (flagOpt.isPresent()) {
                        out.put(flagOpt.get(), FlagValue.parse(rs.getString("flag_value")));
                    }
                }
            }
        }
        return out;
    }
}
