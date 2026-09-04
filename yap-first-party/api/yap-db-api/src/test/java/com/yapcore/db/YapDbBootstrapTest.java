package com.yapcore.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YapDbBootstrapTest {

    @TempDir
    Path tmp;

    @Test
    void findSharedRespectsPreferFlagWithoutBukkitWhenFalse() {
        assertTrue(YapDbBootstrap.findShared(false).isEmpty());
    }

    @Test
    void configureEmbeddedSqliteSetsPoolAndDialect() throws Exception {
        String url = "jdbc:sqlite:" + tmp.resolve("boot.db");
        YapDbBootstrap.Settings settings = YapDbBootstrap.Settings.of(
                "YaPBootstrapTest", url, "", "", 8, 2, 5_000L, false);
        HikariConfig hc = new HikariConfig();
        YapSqlDialect dialect = YapDbBootstrap.configureEmbedded(hc, settings);
        assertEquals(YapDbEngine.SQLITE, dialect.engine());
        assertEquals("YaPBootstrapTest", hc.getPoolName());
        assertEquals(1, hc.getMaximumPoolSize());
        assertEquals(1, hc.getMinimumIdle());
        try (HikariDataSource ds = new HikariDataSource(hc);
             Connection c = ds.getConnection()) {
            assertFalse(c.isClosed());
        }
    }

    @Test
    void openSharedOrEmptyWarnsWhenPreferSharedButUnavailable() {
        String[] warned = {null};
        YapDbBootstrap.Settings settings = YapDbBootstrap.Settings.of(
                "x", "jdbc:sqlite::memory:", "", "", 4, 1, 3_000L, true);
        assertTrue(YapDbBootstrap.openSharedOrEmpty(settings, msg -> warned[0] = msg).isEmpty());
        assertTrue(warned[0] != null && warned[0].contains("embedded"));
    }

    @Test
    void richMysqlPrepCacheFlagsAreAppliedForMysqlUrls() {
        YapDbBootstrap.Settings settings = new YapDbBootstrap.Settings(
                "YaPRich",
                "jdbc:mysql://127.0.0.1:3306/yap",
                "u",
                "p",
                10,
                2,
                5_000L,
                false,
                true);
        HikariConfig hc = new HikariConfig();
        YapSqlDialect dialect = YapDbBootstrap.configureEmbedded(hc, settings);
        assertEquals(YapDbEngine.MYSQL, dialect.engine());
        assertEquals("true", hc.getDataSourceProperties().getProperty("cachePrepStmts"));
        assertEquals("250", hc.getDataSourceProperties().getProperty("prepStmtCacheSize"));
    }
}
