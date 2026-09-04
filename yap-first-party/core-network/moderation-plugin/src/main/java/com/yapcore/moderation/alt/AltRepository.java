package com.yapcore.moderation.alt;

import com.yapcore.db.YapDbEngine;
import com.yapcore.db.YapSqlDialect;
import com.yapcore.moderation.db.ModerationDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AltRepository {

    private final ModerationDatabase database;
    private final YapSqlDialect dialect;

    public AltRepository(ModerationDatabase database) {
        this.database = database;
        this.dialect = database.dialect();
    }

    public void migrate() throws SQLException {
        try (Connection c = database.connection(); Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_mod_known_ips (
                      uuid CHAR(36) NOT NULL,
                      ip_address VARCHAR(45) NOT NULL,
                      last_seen BIGINT NOT NULL,
                      PRIMARY KEY (uuid, ip_address)
                    )
                    """);
            try {
                String sql = dialect.engine() == YapDbEngine.MYSQL
                        ? "CREATE INDEX idx_yap_mod_known_ip ON yap_mod_known_ips (ip_address)"
                        : "CREATE INDEX IF NOT EXISTS idx_yap_mod_known_ip ON yap_mod_known_ips (ip_address)";
                st.execute(sql);
            } catch (SQLException ignored) {
                // already exists
            }
        }
    }

    public void record(UUID uuid, String ip) throws SQLException {
        if (ip == null || ip.isBlank()) {
            return;
        }
        String sql = dialect.upsert(
                "yap_mod_known_ips",
                List.of("uuid", "ip_address"),
                List.of("uuid", "ip_address", "last_seen"),
                Map.of("last_seen", "EXCLUDED.last_seen"));
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, ip);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    public List<AltAccount> findAlts(UUID uuid, String ip) throws SQLException {
        List<AltAccount> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     """
                             SELECT DISTINCT k.uuid, u.name
                             FROM yap_mod_known_ips k
                             LEFT JOIN (
                               SELECT target_uuid AS uuid, target_name AS name
                               FROM yap_mod_punishments
                               WHERE target_uuid IS NOT NULL
                               GROUP BY target_uuid, target_name
                             ) u ON u.uuid = k.uuid
                             WHERE k.ip_address IN (
                               SELECT ip_address FROM yap_mod_known_ips WHERE uuid=?
                             )
                             AND k.uuid <> ?
                             """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("uuid");
                    if (id == null) {
                        continue;
                    }
                    out.add(new AltAccount(UUID.fromString(id), rs.getString("name")));
                }
            }
        }
        if (out.isEmpty() && ip != null) {
            try (Connection c = database.connection();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT uuid FROM yap_mod_known_ips WHERE ip_address=? AND uuid<>?")) {
                ps.setString(1, ip);
                ps.setString(2, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new AltAccount(UUID.fromString(rs.getString("uuid")), null));
                    }
                }
            }
        }
        return out;
    }

    public record AltAccount(UUID uuid, String name) {
    }
}
