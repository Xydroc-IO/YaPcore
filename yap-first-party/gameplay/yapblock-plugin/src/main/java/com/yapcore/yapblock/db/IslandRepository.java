package com.yapcore.yapblock.db;

import com.yapcore.db.YapSqlDialect;
import com.yapcore.yapblock.IslandFlag;
import com.yapcore.yapblock.IslandSnapshot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class IslandRepository {

    private final YapblockDatabase database;

    public IslandRepository(YapblockDatabase database) {
        this.database = database;
    }

    private YapSqlDialect dialect() {
        return database.dialect();
    }

    public IslandSnapshot insert(
            UUID ownerId,
            String name,
            int gridX,
            int gridZ,
            double homeX,
            double homeY,
            double homeZ,
            int sizeRadius,
            int maxMembers,
            int genTier) throws SQLException {
        String sql = """
                INSERT INTO yap_block_islands
                (owner_uuid, name, grid_x, grid_z, home_x, home_y, home_z, size_radius, max_members, gen_tier, level)
                VALUES (?,?,?,?,?,?,?,?,?,?,0)
                """;
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, ownerId.toString());
            ps.setString(2, name);
            ps.setInt(3, gridX);
            ps.setInt(4, gridZ);
            ps.setDouble(5, homeX);
            ps.setDouble(6, homeY);
            ps.setDouble(7, homeZ);
            ps.setInt(8, sizeRadius);
            ps.setInt(9, maxMembers);
            ps.setInt(10, genTier);
            ps.executeUpdate();
            long id;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No generated island id");
                }
                id = keys.getLong(1);
            }
            Map<IslandFlag, Boolean> flags = defaultFlags();
            for (var e : flags.entrySet()) {
                upsertFlag(c, id, e.getKey(), e.getValue());
            }
            return new IslandSnapshot(id, ownerId, name, gridX, gridZ, homeX, homeY, homeZ,
                    sizeRadius, maxMembers, genTier, 0L, Instant.now(), flags);
        }
    }

    public List<IslandSnapshot> loadAll() throws SQLException {
        List<IslandSnapshot> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, owner_uuid, name, grid_x, grid_z, home_x, home_y, home_z, "
                             + "size_radius, max_members, gen_tier, level, created_at FROM yap_block_islands");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                long id = rs.getLong("id");
                out.add(readIsland(rs, loadFlags(c, id)));
            }
        }
        return out;
    }

    public Optional<IslandSnapshot> findById(long id) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, owner_uuid, name, grid_x, grid_z, home_x, home_y, home_z, "
                             + "size_radius, max_members, gen_tier, level, created_at "
                             + "FROM yap_block_islands WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(readIsland(rs, loadFlags(c, id)));
            }
        }
    }

    public void updateHome(long id, double x, double y, double z) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE yap_block_islands SET home_x=?, home_y=?, home_z=? WHERE id=?")) {
            ps.setDouble(1, x);
            ps.setDouble(2, y);
            ps.setDouble(3, z);
            ps.setLong(4, id);
            ps.executeUpdate();
        }
    }

    public void updateLevel(long id, long level) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE yap_block_islands SET level=? WHERE id=?")) {
            ps.setLong(1, level);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    public void updateSize(long id, int sizeRadius) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE yap_block_islands SET size_radius=? WHERE id=?")) {
            ps.setInt(1, sizeRadius);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    public void updateMaxMembers(long id, int maxMembers) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE yap_block_islands SET max_members=? WHERE id=?")) {
            ps.setInt(1, maxMembers);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    public void updateGenTier(long id, int tier) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE yap_block_islands SET gen_tier=? WHERE id=?")) {
            ps.setInt(1, tier);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    public void setFlag(long islandId, IslandFlag flag, boolean value) throws SQLException {
        try (Connection c = database.connection()) {
            upsertFlag(c, islandId, flag, value);
        }
    }

    public void delete(long islandId) throws SQLException {
        try (Connection c = database.connection()) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM yap_block_settings WHERE island_id=?")) {
                ps.setLong(1, islandId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM yap_block_members WHERE island_id=?")) {
                ps.setLong(1, islandId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE yap_block_players SET island_id=NULL WHERE island_id=?")) {
                ps.setLong(1, islandId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM yap_block_islands WHERE id=?")) {
                ps.setLong(1, islandId);
                ps.executeUpdate();
            }
        }
    }

    private void upsertFlag(Connection c, long islandId, IslandFlag flag, boolean value) throws SQLException {
        Map<String, String> set = new LinkedHashMap<>();
        set.put("value", "EXCLUDED.value");
        String sql = dialect().upsert(
                "yap_block_settings",
                List.of("island_id", "flag"),
                List.of("island_id", "flag", "value"),
                set);
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, islandId);
            ps.setString(2, flag.name());
            ps.setBoolean(3, value);
            ps.executeUpdate();
        }
    }

    private Map<IslandFlag, Boolean> loadFlags(Connection c, long islandId) throws SQLException {
        Map<IslandFlag, Boolean> flags = defaultFlags();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT flag, value FROM yap_block_settings WHERE island_id=?")) {
            ps.setLong(1, islandId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        IslandFlag flag = IslandFlag.valueOf(rs.getString("flag"));
                        flags.put(flag, rs.getBoolean("value"));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }
        return flags;
    }

    private static Map<IslandFlag, Boolean> defaultFlags() {
        EnumMap<IslandFlag, Boolean> flags = new EnumMap<>(IslandFlag.class);
        for (IslandFlag flag : IslandFlag.values()) {
            flags.put(flag, flag.defaultValue());
        }
        return flags;
    }

    private static IslandSnapshot readIsland(ResultSet rs, Map<IslandFlag, Boolean> flags) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        Instant instant = created == null ? Instant.EPOCH : created.toInstant();
        return new IslandSnapshot(
                rs.getLong("id"),
                UUID.fromString(rs.getString("owner_uuid")),
                rs.getString("name"),
                rs.getInt("grid_x"),
                rs.getInt("grid_z"),
                rs.getDouble("home_x"),
                rs.getDouble("home_y"),
                rs.getDouble("home_z"),
                rs.getInt("size_radius"),
                rs.getInt("max_members"),
                rs.getInt("gen_tier"),
                rs.getLong("level"),
                instant,
                flags);
    }
}
