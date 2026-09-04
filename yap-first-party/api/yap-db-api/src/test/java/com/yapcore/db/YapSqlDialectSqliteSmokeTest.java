package com.yapcore.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Live smoke: dialect upsert + insertIgnore against embedded SQLite. */
final class YapSqlDialectSqliteSmokeTest {

    @Test
    void sqliteUpsertAndInsertIgnoreRoundTrip() throws Exception {
        YapSqlDialect d = YapSqlDialects.sqlite();
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:sqlite::memory:");
        hc.setMaximumPoolSize(1);
        try (HikariDataSource ds = new HikariDataSource(hc);
             Connection c = ds.getConnection();
             Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE kit_cooldowns (
                      uuid TEXT NOT NULL,
                      kit TEXT NOT NULL,
                      claimed_at TEXT NOT NULL,
                      uses INTEGER NOT NULL DEFAULT 1,
                      PRIMARY KEY (uuid, kit)
                    )
                    """);

            String ignore = d.insertIgnore("kit_cooldowns", List.of("uuid", "kit", "claimed_at", "uses"));
            try (PreparedStatement ps = c.prepareStatement(ignore)) {
                ps.setString(1, "u1");
                ps.setString(2, "starter");
                ps.setString(3, "2026-01-01");
                ps.setInt(4, 1);
                assertEquals(1, ps.executeUpdate());
            }
            try (PreparedStatement ps = c.prepareStatement(ignore)) {
                ps.setString(1, "u1");
                ps.setString(2, "starter");
                ps.setString(3, "2026-01-02");
                ps.setInt(4, 99);
                assertEquals(0, ps.executeUpdate());
            }

            String upsert = d.upsert(
                    "kit_cooldowns",
                    List.of("uuid", "kit"),
                    List.of("uuid", "kit", "claimed_at", "uses"),
                    Map.of("claimed_at", "EXCLUDED.claimed_at", "uses", "uses + 1"));
            try (PreparedStatement ps = c.prepareStatement(upsert)) {
                ps.setString(1, "u1");
                ps.setString(2, "starter");
                ps.setString(3, "2026-09-04");
                ps.setInt(4, 1);
                ps.executeUpdate();
            }

            try (ResultSet rs = st.executeQuery(
                    "SELECT claimed_at, uses FROM kit_cooldowns WHERE uuid='u1' AND kit='starter'")) {
                rs.next();
                assertEquals("2026-09-04", rs.getString(1));
                assertEquals(2, rs.getInt(2));
            }
        }
    }
}
