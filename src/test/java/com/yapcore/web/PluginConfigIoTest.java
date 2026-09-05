package com.yapcore.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginConfigIoTest {

    @TempDir
    Path tmp;

    @Test
    void flattenAndApplyRoundTripNestedYaml() throws Exception {
        Map<String, Object> yaml = new LinkedHashMap<>();
        yaml.put("enabled", true);
        Map<String, Object> jdbc = new LinkedHashMap<>();
        jdbc.put("url", "jdbc:mysql://localhost/yap");
        jdbc.put("user", "yap");
        yaml.put("jdbc", jdbc);
        yaml.put("worlds", List.of("world", "nether"));
        List<Map<String, Object>> fields = PluginConfigIo.flatten(yaml);
        assertTrue(fields.stream().anyMatch(f -> "enabled".equals(f.get("key"))));
        assertTrue(fields.stream().anyMatch(f -> "jdbc.url".equals(f.get("key"))));
        Map<String, Object> jdbcUrl = fields.stream()
                .filter(f -> "jdbc.url".equals(f.get("key")))
                .findFirst().orElseThrow();
        assertEquals("Database address", jdbcUrl.get("title"));
        assertEquals("Database", jdbcUrl.get("group"));
        assertEquals(Boolean.TRUE, jdbcUrl.get("advanced"));
        assertEquals("world, nether", fields.stream()
                .filter(f -> "worlds".equals(f.get("key")))
                .findFirst().orElseThrow()
                .get("value"));

        PluginConfigIo.apply(yaml, "enabled", "false");
        PluginConfigIo.apply(yaml, "jdbc.user", "ops");
        assertEquals(Boolean.FALSE, yaml.get("enabled"));
        assertEquals("ops", ((Map<?, ?>) yaml.get("jdbc")).get("user"));
    }

    @Test
    void saveWritesPluginConfigFile() throws Exception {
        PluginConfigCatalog.Entry entry = PluginConfigCatalog.byId("yap-stacker");
        Path dir = tmp.resolve("plugins").resolve(entry.dataDir());
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(entry.file()), "enabled: true\nmobs:\n  max-stack: 100\n");
        PluginConfigIo.save(tmp, entry, Map.of(
                "action", "save",
                "plugin", "yap-stacker",
                "enabled", "false",
                "mobs.max-stack", "250"));
        Map<String, Object> loaded = PluginConfigIo.load(tmp, entry);
        assertEquals(Boolean.FALSE, loaded.get("enabled"));
        assertEquals(250, ((Number) ((Map<?, ?>) loaded.get("mobs")).get("max-stack")).intValue());
    }

    @Test
    void catalogCoversCoreAndGameplayPlugins() {
        assertTrue(PluginConfigCatalog.all().size() >= 30);
        assertTrue(PluginConfigCatalog.byId("yap-factions") != null);
        assertTrue(PluginConfigCatalog.byId("yap-skills") != null);
        assertTrue(PluginConfigCatalog.byId("yap-disasters") != null);
        assertTrue(PluginConfigCatalog.byId("yap-db") != null);
        assertTrue(PluginConfigCatalog.byId("yap-guilds") == null);
        assertTrue(PluginConfigCatalog.byId("yap-combat") == null);
    }
}
