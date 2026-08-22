package com.yapcore.moderation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;

/** JDBC ban lookup for YaP Link proxy plugins (shared {@code yap_mod_punishments} schema). */
public final class ProxyModerationLookup {

    private ProxyModerationLookup() {
    }

    public record BanHit(String reason) {
    }

    public static Optional<BanHit> findActiveBan(Connection c, String uuid, String ip) throws Exception {
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT reason FROM yap_mod_punishments
                WHERE active = 1 AND type IN ('BAN', 'IP_BAN')
                  AND (expires_at = 0 OR expires_at > ?)
                  AND (target_uuid = ? OR ip_address = ?)
                ORDER BY created_at DESC LIMIT 1
                """)) {
            ps.setLong(1, now);
            ps.setString(2, uuid);
            ps.setString(3, ip);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new BanHit(rs.getString("reason")));
                }
            }
        }
        return Optional.empty();
    }
}
