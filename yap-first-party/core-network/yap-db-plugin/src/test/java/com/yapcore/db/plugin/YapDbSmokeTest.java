package com.yapcore.db.plugin;

import com.yapcore.db.YapDbEngine;
import com.yapcore.db.YapSqlDialects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YapDbSmokeTest {

    @TempDir
    Path tmp;

    @Test
    void dialectSelectionMatchesJdbcUrls() {
        assertEquals(YapDbEngine.MYSQL,
                YapSqlDialects.resolve("jdbc:mysql://127.0.0.1:3306/yap", "auto").engine());
        assertEquals(YapDbEngine.POSTGRES,
                YapSqlDialects.resolve("jdbc:postgresql://127.0.0.1:5432/yap", "auto").engine());
        assertEquals(YapDbEngine.SQLITE,
                YapSqlDialects.resolve("jdbc:sqlite:data/yap.db", "auto").engine());
        assertEquals(YapDbEngine.SQLITE,
                YapSqlDialects.resolve("jdbc:mysql://x", "sqlite").engine());
        assertEquals(YapDbEngine.POSTGRES,
                YapSqlDialects.resolve("jdbc:mysql://x", "postgres").engine());
    }

    @Test
    void ensureSqliteParentCreatesDirectories() throws Exception {
        Path db = tmp.resolve("nested/dir/yap.db");
        YapDbPlugin.ensureSqliteParent("jdbc:sqlite:" + db);
        assertTrue(Files.isDirectory(db.getParent()));

        YapDbPlugin.ensureSqliteParent("jdbc:sqlite::memory:");
        YapDbPlugin.ensureSqliteParent("jdbc:mysql://127.0.0.1:3306/yap");
        assertTrue(Files.isDirectory(db.getParent()));
    }

    @Test
    void pluginYmlAndMainClassLoad() throws Exception {
        assertEquals(YapDbPlugin.class, Class.forName("com.yapcore.db.plugin.YapDbPlugin"));
        try (InputStream in = YapDbPlugin.class.getResourceAsStream("/plugin.yml")) {
            assertNotNull(in);
            String yml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(yml.contains("name: YaPDB"));
            assertTrue(yml.contains("main: com.yapcore.db.plugin.YapDbPlugin"));
            assertTrue(yml.contains("load: STARTUP"));
            assertTrue(yml.contains("yapdb.admin"));
        }
    }
}
