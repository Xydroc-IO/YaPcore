package com.yapcore.fleet.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DatabaseSetupTest {

    @TempDir
    Path root;

    @Test
    void parseEnginesFromLabelsAndUrls() {
        assertEquals(DatabaseSetup.Engine.MYSQL, DatabaseSetup.Engine.parse("mysql"));
        assertEquals(DatabaseSetup.Engine.POSTGRES, DatabaseSetup.Engine.parse("PostgreSQL"));
        assertEquals(DatabaseSetup.Engine.SQLITE, DatabaseSetup.Engine.parse("sqlite"));
        assertEquals(DatabaseSetup.Engine.POSTGRES,
                DatabaseSetup.Engine.fromJdbcUrl("jdbc:postgresql://127.0.0.1:5432/yap"));
        assertEquals(DatabaseSetup.Engine.SQLITE,
                DatabaseSetup.Engine.fromJdbcUrl("jdbc:sqlite:data/yap.db"));
    }

    @Test
    void statusReportsMissingConfigCleanly() {
        Map<String, Object> s = DatabaseSetup.status(root);
        assertTrue(Boolean.TRUE.equals(s.get("ok")));
        @SuppressWarnings("unchecked")
        Map<String, Object> yapdb = (Map<String, Object>) s.get("yapdb");
        assertEquals(false, yapdb.get("configPresent"));
        assertTrue(s.get("engines") instanceof java.util.List);
    }

    @Test
    void statusReadsExistingYapdbConfig() throws Exception {
        Path cfg = root.resolve("plugins/YaPDB/config.yml");
        Files.createDirectories(cfg.getParent());
        Files.writeString(cfg, """
                jdbc:
                  engine: postgres
                  url: jdbc:postgresql://10.0.0.5:5432/yap_playerdata
                  user: yap
                  password: secret
                """);
        Map<String, Object> s = DatabaseSetup.status(root);
        @SuppressWarnings("unchecked")
        Map<String, Object> yapdb = (Map<String, Object>) s.get("yapdb");
        assertEquals(true, yapdb.get("configPresent"));
        assertEquals("postgres", yapdb.get("engine"));
        assertEquals("jdbc:postgresql://10.0.0.5:5432/yap_playerdata", yapdb.get("jdbcUrl"));
        assertEquals("yap", yapdb.get("user"));
    }

    @Test
    void sqlitePresetPointsAtDataDir() {
        String url = DatabaseSetup.Engine.SQLITE.presetJdbcUrl(root);
        assertTrue(url.contains("data/yap.db"), url);
        assertTrue(url.startsWith("jdbc:sqlite:"), url);
    }
}
