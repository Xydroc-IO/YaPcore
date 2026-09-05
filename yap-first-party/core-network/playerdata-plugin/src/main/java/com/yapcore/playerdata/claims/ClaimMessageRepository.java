package com.yapcore.playerdata.claims;

import com.yapcore.db.YapSqlDialect;
import com.yapcore.playerdata.db.Database;
import com.yapcore.regions.RegionMessageKind;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Persists claim greeting/farewell strings (separate from allow/deny flags). */
public final class ClaimMessageRepository {

    private final Database database;
    private final YapSqlDialect dialect;
    private final Map<Long, Map<RegionMessageKind, String>> cache = new ConcurrentHashMap<>();

    public ClaimMessageRepository(Database database) {
        this.database = database;
        this.dialect = database.dialect();
    }

    public Optional<String> get(long claimId, RegionMessageKind kind) {
        Map<RegionMessageKind, String> map = cache.computeIfAbsent(claimId, this::loadSafe);
        return Optional.ofNullable(map.get(kind));
    }

    public void set(long claimId, RegionMessageKind kind, String text) throws SQLException {
        String sql = dialect.upsert(
                "yap_claim_messages",
                List.of("claim_id", "kind"),
                List.of("claim_id", "kind", "message_text"),
                Map.of("message_text", "EXCLUDED.message_text"));
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, claimId);
            ps.setString(2, kind.name());
            ps.setString(3, text);
            ps.executeUpdate();
        }
        cache.computeIfAbsent(claimId, id -> new ConcurrentHashMap<>()).put(kind, text);
    }

    public void clear(long claimId, RegionMessageKind kind) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM yap_claim_messages WHERE claim_id = ? AND kind = ?")) {
            ps.setLong(1, claimId);
            ps.setString(2, kind.name());
            ps.executeUpdate();
        }
        Map<RegionMessageKind, String> map = cache.get(claimId);
        if (map != null) {
            map.remove(kind);
        }
    }

    public void invalidate(long claimId) {
        cache.remove(claimId);
    }

    public void invalidateAll() {
        cache.clear();
    }

    private Map<RegionMessageKind, String> loadSafe(long claimId) {
        try {
            return load(claimId);
        } catch (SQLException e) {
            return new ConcurrentHashMap<>();
        }
    }

    private Map<RegionMessageKind, String> load(long claimId) throws SQLException {
        Map<RegionMessageKind, String> out = new ConcurrentHashMap<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT kind, message_text FROM yap_claim_messages WHERE claim_id = ?")) {
            ps.setLong(1, claimId);
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
