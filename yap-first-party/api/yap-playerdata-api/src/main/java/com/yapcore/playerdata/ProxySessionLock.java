package com.yapcore.playerdata;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** JDBC session lock lookup for YaP Link server-selector (shared playerdata schema). */
public final class ProxySessionLock {

    private ProxySessionLock() {
    }

    public static Optional<String> lockHolder(Connection c, UUID uuid) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT lock_server FROM yap_players WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String holder = rs.getString("lock_server");
                    Instant until = rs.getTimestamp("lock_until") != null
                            ? rs.getTimestamp("lock_until").toInstant() : null;
                    if (holder != null && !holder.isBlank()
                            && (until == null || until.isAfter(Instant.now()))) {
                        return Optional.of(holder);
                    }
                }
            }
        }
        return Optional.empty();
    }
}
