package com.yapcore.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardHoloUtilTest {

    @TempDir
    Path tmp;

    @Test
    void parsesConsoleJson() {
        String raw = "ok\nYAPHOLO_JSON:[{\"id\":\"spawn\",\"world\":\"world\",\"x\":0.5,\"y\":66.0,\"z\":-2.0,\"view\":48.0,\"lines\":\"&6Welcome|&7hub\"}]\n";
        List<Map<String, Object>> rows = DashboardHoloUtil.parseListJson(raw);
        assertEquals(1, rows.size());
        assertEquals("spawn", rows.get(0).get("id"));
        assertEquals("world", rows.get(0).get("world"));
        assertEquals("&6Welcome|&7hub", rows.get(0).get("lines"));
        assertEquals(0.5, ((Number) rows.get(0).get("x")).doubleValue(), 1e-9);
    }

    @Test
    void readsPersistedYaml() throws Exception {
        Path dir = tmp.resolve("plugins").resolve("YaPHolo");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("holograms.yml"), """
                holograms:
                  spawn:
                    world: world
                    x: 1.5
                    y: 70
                    z: 8
                    view-distance: 32
                    lines:
                      - "&6Hello"
                      - "&7there"
                """);
        List<Map<String, Object>> rows = DashboardHoloUtil.fromYaml(tmp);
        assertEquals(1, rows.size());
        assertEquals("spawn", rows.get(0).get("id"));
        assertEquals("world", rows.get(0).get("world"));
        assertEquals("&6Hello|&7there", rows.get(0).get("lines"));
        assertTrue(DashboardHoloUtil.parseListJson("no json here").isEmpty());
    }
}
