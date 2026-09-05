package com.yapcore.regions.db;

import com.yapcore.db.YapSqlDialect;
import com.yapcore.regions.AdminRegion;
import com.yapcore.regions.FlagValue;
import com.yapcore.regions.RegionFlag;
import com.yapcore.regions.RegionShape;
import com.yapcore.regions.RegionVertex;

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

    private final RegionSql database;
    private final YapSqlDialect dialect;

    public AdminRegionRepository(RegionsDatabase database) {
        this((RegionSql) database);
    }

    public AdminRegionRepository(RegionSql database) {
        this.database = database;
        this.dialect = database.dialect();
    }

    public List<AdminRegion> loadForServer(String serverId) throws SQLException {
        List<AdminRegion> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT id, server_id, world, min_x, max_x, min_y, max_y, min_z, max_z, name, priority, shape
                     FROM yap_admin_regions WHERE server_id = ?
                     ORDER BY name
                     """)) {
            ps.setString(1, serverId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    out.add(mapRegion(rs, loadFlags(c, id), loadVertices(c, id)));
                }
            }
        }
        return out;
    }

    public Optional<AdminRegion> findByName(String serverId, String name) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT id, server_id, world, min_x, max_x, min_y, max_y, min_z, max_z, name, priority, shape
                     FROM yap_admin_regions WHERE server_id = ? AND name = ?
                     """)) {
            ps.setString(1, serverId);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                long id = rs.getLong("id");
                return Optional.of(mapRegion(rs, loadFlags(c, id), loadVertices(c, id)));
            }
        }
    }

    public long create(String serverId, String name, String world,
                       int minX, int maxX, int minY, int maxY, int minZ, int maxZ) throws SQLException {
        return createShape(serverId, name, world, minX, maxX, minY, maxY, minZ, maxZ,
                RegionShape.CUBOID, List.of());
    }

    public long createPolygon(String serverId, String name, String world,
                              int minY, int maxY, List<RegionVertex> vertices) throws SQLException {
        if (vertices == null || vertices.size() < 3) {
            throw new SQLException("Polygon requires at least 3 vertices");
        }
        Bounds b = boundsOf(vertices, minY, maxY);
        return createShape(serverId, name, world, b.minX, b.maxX, b.minY, b.maxY, b.minZ, b.maxZ,
                RegionShape.POLYGON, vertices);
    }

    private long createShape(String serverId, String name, String world,
                             int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
                             RegionShape shape, List<RegionVertex> vertices) throws SQLException {
        try (Connection c = database.connection()) {
            long id;
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO yap_admin_regions
                    (server_id, name, world, min_x, max_x, min_y, max_y, min_z, max_z, priority, shape)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
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
                ps.setString(10, shape.name());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("No generated key for admin region");
                    }
                    id = keys.getLong(1);
                }
            }
            replaceVertices(c, id, vertices);
            return id;
        }
    }

    public void updateBounds(long regionId, String world,
                             int minX, int maxX, int minY, int maxY, int minZ, int maxZ) throws SQLException {
        try (Connection c = database.connection()) {
            try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE yap_admin_regions
                    SET world = ?, min_x = ?, max_x = ?, min_y = ?, max_y = ?, min_z = ?, max_z = ?, shape = ?
                    WHERE id = ?
                    """)) {
                ps.setString(1, world);
                ps.setInt(2, minX);
                ps.setInt(3, maxX);
                ps.setInt(4, minY);
                ps.setInt(5, maxY);
                ps.setInt(6, minZ);
                ps.setInt(7, maxZ);
                ps.setString(8, RegionShape.CUBOID.name());
                ps.setLong(9, regionId);
                if (ps.executeUpdate() == 0) {
                    throw new SQLException("Region id not found: " + regionId);
                }
            }
            replaceVertices(c, regionId, List.of());
        }
    }

    public void updatePolygon(long regionId, String world, int minY, int maxY,
                              List<RegionVertex> vertices) throws SQLException {
        if (vertices == null || vertices.size() < 3) {
            throw new SQLException("Polygon requires at least 3 vertices");
        }
        Bounds b = boundsOf(vertices, minY, maxY);
        try (Connection c = database.connection()) {
            try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE yap_admin_regions
                    SET world = ?, min_x = ?, max_x = ?, min_y = ?, max_y = ?, min_z = ?, max_z = ?, shape = ?
                    WHERE id = ?
                    """)) {
                ps.setString(1, world);
                ps.setInt(2, b.minX);
                ps.setInt(3, b.maxX);
                ps.setInt(4, b.minY);
                ps.setInt(5, b.maxY);
                ps.setInt(6, b.minZ);
                ps.setInt(7, b.maxZ);
                ps.setString(8, RegionShape.POLYGON.name());
                ps.setLong(9, regionId);
                if (ps.executeUpdate() == 0) {
                    throw new SQLException("Region id not found: " + regionId);
                }
            }
            replaceVertices(c, regionId, vertices);
        }
    }

    public void setPriority(long regionId, int priority) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     UPDATE yap_admin_regions SET priority = ? WHERE id = ?
                     """)) {
            ps.setInt(1, priority);
            ps.setLong(2, regionId);
            if (ps.executeUpdate() == 0) {
                throw new SQLException("Region id not found: " + regionId);
            }
        }
    }

    public void delete(long regionId) throws SQLException {
        try (Connection c = database.connection()) {
            try (PreparedStatement messages = c.prepareStatement(
                    "DELETE FROM yap_admin_region_messages WHERE region_id = ?")) {
                messages.setLong(1, regionId);
                messages.executeUpdate();
            }
            try (PreparedStatement flags = c.prepareStatement(
                    "DELETE FROM yap_admin_region_flags WHERE region_id = ?")) {
                flags.setLong(1, regionId);
                flags.executeUpdate();
            }
            try (PreparedStatement verts = c.prepareStatement(
                    "DELETE FROM yap_admin_region_vertices WHERE region_id = ?")) {
                verts.setLong(1, regionId);
                verts.executeUpdate();
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
        String sql = dialect.upsert(
                "yap_admin_region_flags",
                List.of("region_id", "flag_name"),
                List.of("region_id", "flag_name", "flag_value"),
                Map.of("flag_value", "EXCLUDED.flag_value"));
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, regionId);
            ps.setString(2, flag.name());
            ps.setString(3, value.name());
            ps.executeUpdate();
        }
    }

    public void clearFlags(long regionId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM yap_admin_region_flags WHERE region_id = ?")) {
            ps.setLong(1, regionId);
            ps.executeUpdate();
        }
    }

    public Map<RegionFlag, FlagValue> loadFlagsPublic(long regionId) throws SQLException {
        try (Connection c = database.connection()) {
            return loadFlags(c, regionId);
        }
    }

    private static AdminRegion mapRegion(ResultSet rs, Map<RegionFlag, FlagValue> flags,
                                         List<RegionVertex> vertices) throws SQLException {
        RegionShape shape = RegionShape.parse(rs.getString("shape")).orElse(RegionShape.CUBOID);
        if (shape == RegionShape.POLYGON && vertices.size() < 3) {
            shape = RegionShape.CUBOID;
        }
        return new AdminRegion(
                rs.getLong("id"),
                rs.getString("server_id"),
                rs.getString("world"),
                rs.getInt("min_x"),
                rs.getInt("max_x"),
                rs.getInt("min_y"),
                rs.getInt("max_y"),
                rs.getInt("min_z"),
                rs.getInt("max_z"),
                rs.getString("name"),
                rs.getInt("priority"),
                flags,
                shape,
                vertices);
    }

    private Map<RegionFlag, FlagValue> loadFlags(Connection c, long regionId) throws SQLException {
        Map<RegionFlag, FlagValue> out = new EnumMap<>(RegionFlag.class);
        try (PreparedStatement ps = c.prepareStatement("""
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

    private List<RegionVertex> loadVertices(Connection c, long regionId) throws SQLException {
        List<RegionVertex> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("""
                     SELECT x, z FROM yap_admin_region_vertices WHERE region_id = ? ORDER BY seq
                     """)) {
            ps.setLong(1, regionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new RegionVertex(rs.getInt("x"), rs.getInt("z")));
                }
            }
        }
        return List.copyOf(out);
    }

    private static void replaceVertices(Connection c, long regionId, List<RegionVertex> vertices)
            throws SQLException {
        try (PreparedStatement del = c.prepareStatement(
                "DELETE FROM yap_admin_region_vertices WHERE region_id = ?")) {
            del.setLong(1, regionId);
            del.executeUpdate();
        }
        if (vertices == null || vertices.isEmpty()) {
            return;
        }
        try (PreparedStatement ins = c.prepareStatement("""
                INSERT INTO yap_admin_region_vertices (region_id, seq, x, z) VALUES (?, ?, ?, ?)
                """)) {
            int seq = 0;
            for (RegionVertex v : vertices) {
                ins.setLong(1, regionId);
                ins.setInt(2, seq++);
                ins.setInt(3, v.x());
                ins.setInt(4, v.z());
                ins.addBatch();
            }
            ins.executeBatch();
        }
    }

    private static Bounds boundsOf(List<RegionVertex> vertices, int minY, int maxY) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (RegionVertex v : vertices) {
            minX = Math.min(minX, v.x());
            maxX = Math.max(maxX, v.x());
            minZ = Math.min(minZ, v.z());
            maxZ = Math.max(maxZ, v.z());
        }
        return new Bounds(minX, maxX, Math.min(minY, maxY), Math.max(minY, maxY), minZ, maxZ);
    }

    private record Bounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
    }
}
