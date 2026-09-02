package com.yapcore.perms.db;

import com.yapcore.perms.engine.StoredNode;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;

final class PermsSql {
    private PermsSql() {
    }

    static StoredNode readNode(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("expires_at");
        Instant exp = ts == null ? null : ts.toInstant();
        return new StoredNode(
                rs.getString("node"),
                rs.getInt("value") == 1,
                empty(rs.getString("world")),
                empty(rs.getString("server_ctx")),
                exp);
    }

    static void bindExpiry(PreparedStatement ps, int index, Instant expires) throws SQLException {
        if (expires == null) {
            ps.setNull(index, Types.TIMESTAMP);
        } else {
            ps.setTimestamp(index, Timestamp.from(expires));
        }
    }

    static String empty(String raw) {
        return raw == null ? "" : raw;
    }
}
