package com.yapcore.regions.db;

import com.yapcore.db.YapSqlDialect;
import com.yapcore.regions.RegionMessageKind;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Greeting / farewell text for admin regions. */
public final class RegionMessageRepository {

    private final RegionSql database;
    private final YapSqlDialect dialect;
    private final Map<Long, Map<RegionMessageKind, String>> cache = new ConcurrentHashMap<>();

    public RegionMessageRepository(RegionsDatabase database) {
        this((RegionSql) database);
    }

    public RegionMessageRepository(RegionSql database) {
        this.database = database;
        this.dialect = database.dialect();
    }

    public Optional<String> get(long regionId, RegionMessageKind kind) {
        Map<RegionMessageKind, String> map = cache.computeIfAbsent(regionId, this::loadSafe);
        return Optional.ofNullable(map.get(kind));
    }

    public void set(long regionId, RegionMessageKind kind, String text) throws SQLException {
        String sql = dialect.upsert(
                "yap_admin_region_messages",
                List.of("region_id", "kind"),
                List.of("region_id", "kind", "message_text"),
                Map.of("message_text", "EXCLUDED.message_text"));
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, regionId);
            ps.setString(2, kind.name());
            ps.setString(3, text);
            ps.executeUpdate();
        }
        cache.computeIfAbsent(regionId, id -> new ConcurrentHashMap<>()).put(kind, text);
    }

    public void clear(long regionId, RegionMessageKind kind) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM yap_admin_region_messages WHERE region_id = ? AND kind = ?")) {
            ps.setLong(1, regionId);
            ps.setString(2, kind.name());
            ps.executeUpdate();
        }
        Map<RegionMessageKind, String> map = cache.get(regionId);
        if (map != null) {
            map.remove(kind);
        }
    }

    public void deleteForRegion(long regionId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM yap_admin_region_messages WHERE region_id = ?")) {
            ps.setLong(1, regionId);
            ps.executeUpdate();
        }
        cache.remove(regionId);
    }

    public void invalidateAll() {
        cache.clear();
    }

    private Map<RegionMessageKind, String> loadSafe(long regionId) {
        try {
            return load(regionId);
        } catch (SQLException e) {
            return new ConcurrentHashMap<>();
        }
    }

    private Map<RegionMessageKind, String> load(long regionId) throws SQLException {
        Map<RegionMessageKind, String> out = new ConcurrentHashMap<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT kind, message_text FROM yap_admin_region_messages WHERE region_id = ?")) {
            ps.setLong(1, regionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    var kindOpt = RegionMessageKind.parse(rs.getString("kind"));
                    if (kindOpt.isPresent()) {
                        out.put(kindOpt.get(), rs.getString("message_text"));
                    }
                }
            }
        }
        return out;
    }
}
