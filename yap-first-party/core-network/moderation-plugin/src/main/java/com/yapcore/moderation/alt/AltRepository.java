package com.yapcore.moderation.alt;

import com.yapcore.moderation.db.ModerationDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AltRepository {

    private final ModerationDatabase database;

    public AltRepository(ModerationDatabase database) {
        this.database = database;
    }

    public void migrate() throws SQLException {
        try (Connection c = database.connection(); Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_mod_known_ips (
                      uuid CHAR(36) NOT NULL,
                      ip_address VARCHAR(45) NOT NULL,
                      last_seen BIGINT NOT NULL,
                      PRIMARY KEY (uuid, ip_address),
                      INDEX idx_ip (ip_address)
                    )
                    """);
        }
    }

    public void record(UUID uuid, String ip) throws SQLException {
        if (ip == null || ip.isBlank()) {
            return;
        }
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO yap_mod_known_ips (uuid, ip_address, last_seen) VALUES (?,?,?) "
                             + "ON DUPLICATE KEY UPDATE last_seen=VALUES(last_seen)")) {
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
