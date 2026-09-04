package com.yapcore.mmocontent.db;

import com.yapcore.db.YapSqlDialect;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BossKillRepository {

    private final ContentDatabase database;

    public BossKillRepository(ContentDatabase database) {
        this.database = database;
    }

    public int increment(UUID playerUuid, String bossId) throws SQLException {
        YapSqlDialect dialect = database.dialect();
        Map<String, String> set = new LinkedHashMap<>();
        set.put("kill_count", "kill_count + 1");
        set.put("updated_at", dialect.nowFn());
        String sql = dialect.upsert(
                "yap_mmo_boss_kills",
                List.of("player_uuid", "boss_id"),
                List.of("player_uuid", "boss_id", "kill_count"),
                set);
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, bossId);
            ps.setInt(3, 1);
            ps.executeUpdate();
        }
        return getCount(playerUuid, bossId);
    }

    public int getCount(UUID playerUuid, String bossId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT kill_count FROM yap_mmo_boss_kills
                     WHERE player_uuid = ? AND boss_id = ?
                     """)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, bossId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("kill_count") : 0;
            }
        }
    }

    public Map<String, Integer> totalKillsByBoss() throws SQLException {
        Map<String, Integer> out = new LinkedHashMap<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT boss_id, SUM(kill_count) AS total FROM yap_mmo_boss_kills
                     GROUP BY boss_id ORDER BY boss_id
                     """)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getString("boss_id"), rs.getInt("total"));
                }
            }
        }
        return out;
    }
}
