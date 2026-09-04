package com.yapcore.db;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class YapSqlDialectTest {

    @Test
    void detectsEnginesFromJdbcUrl() {
        assertEquals(YapDbEngine.MYSQL, YapSqlDialects.fromJdbcUrl("jdbc:mysql://127.0.0.1:3306/yap").engine());
        assertEquals(YapDbEngine.MYSQL, YapSqlDialects.fromJdbcUrl("jdbc:mariadb://127.0.0.1:3306/yap").engine());
        assertEquals(YapDbEngine.POSTGRES, YapSqlDialects.fromJdbcUrl("jdbc:postgresql://127.0.0.1:5432/yap").engine());
        assertEquals(YapDbEngine.SQLITE, YapSqlDialects.fromJdbcUrl("jdbc:sqlite:data/yap.db").engine());
    }

    @Test
    void resolveEngineOverride() {
        assertEquals(YapDbEngine.POSTGRES,
                YapSqlDialects.resolve("jdbc:mysql://x", "postgres").engine());
        assertEquals(YapDbEngine.SQLITE,
                YapSqlDialects.resolve("jdbc:mysql://x", "sqlite").engine());
        assertEquals(YapDbEngine.MYSQL,
                YapSqlDialects.resolve("jdbc:mysql://x", "auto").engine());
        assertEquals(YapDbEngine.POSTGRES,
                YapSqlDialects.resolve("jdbc:postgresql://x", "auto").engine());
    }

    @Test
    void upsertMysqlUsesOnDuplicate() {
        YapSqlDialect d = YapSqlDialects.mysql();
        String sql = d.upsert(
                "player_profiles",
                List.of("uuid", "profile"),
                List.of("uuid", "profile", "xp"),
                Map.of("xp", "EXCLUDED.xp"));
        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(sql.contains("xp = VALUES(xp)"));
        assertTrue(sql.contains("VALUES (?, ?, ?)"));
    }

    @Test
    void upsertPostgresUsesOnConflict() {
        YapSqlDialect d = YapSqlDialects.postgres();
        Map<String, String> set = new LinkedHashMap<>();
        set.put("xp", "EXCLUDED.xp");
        set.put("level", "EXCLUDED.level");
        String sql = d.upsert(
                "player_profiles",
                List.of("uuid", "profile"),
                List.of("uuid", "profile", "xp", "level"),
                set);
        assertTrue(sql.contains("ON CONFLICT (uuid, profile) DO UPDATE SET"));
        assertTrue(sql.contains("xp = EXCLUDED.xp"));
        assertTrue(sql.contains("level = EXCLUDED.level"));
    }

    @Test
    void upsertSqliteUsesExcludedLowercase() {
        YapSqlDialect d = YapSqlDialects.sqlite();
        String sql = d.upsert(
                "homes",
                List.of("uuid", "name"),
                List.of("uuid", "name", "world"),
                Map.of("world", "EXCLUDED.world"));
        assertTrue(sql.contains("ON CONFLICT (uuid, name) DO UPDATE SET"));
        assertTrue(sql.contains("world = excluded.world"));
    }

    @Test
    void insertIgnoreVariants() {
        assertTrue(YapSqlDialects.mysql().insertIgnore("t", List.of("a", "b")).startsWith("INSERT IGNORE INTO t"));
        assertTrue(YapSqlDialects.postgres().insertIgnore("t", List.of("a")).contains("ON CONFLICT DO NOTHING"));
        assertTrue(YapSqlDialects.sqlite().insertIgnore("t", List.of("a")).startsWith("INSERT OR IGNORE INTO t"));
    }

    @Test
    void typeHelpersDifferByEngine() {
        assertEquals("BIGINT AUTO_INCREMENT PRIMARY KEY", YapSqlDialects.mysql().autoIncrementPk());
        assertEquals("BIGSERIAL PRIMARY KEY", YapSqlDialects.postgres().autoIncrementPk());
        assertEquals("INTEGER PRIMARY KEY AUTOINCREMENT", YapSqlDialects.sqlite().autoIncrementPk());
        assertEquals("BYTEA", YapSqlDialects.postgres().blobType());
        assertEquals("BLOB", YapSqlDialects.sqlite().blobType());
        assertTrue(YapSqlDialects.mysql().timestampTouchColumn("updated_at").contains("ON UPDATE CURRENT_TIMESTAMP"));
        assertTrue(!YapSqlDialects.postgres().timestampTouchColumn("updated_at").contains("ON UPDATE"));
    }

    @Test
    void sqlitePrefersPoolSizeOne() {
        assertEquals(1, YapSqlDialects.sqlite().preferMaxPoolSize(16));
        assertEquals(16, YapSqlDialects.mysql().preferMaxPoolSize(16));
    }
}
