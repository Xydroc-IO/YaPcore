package com.yapcore.fleet.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yapcore.fleet.model.FleetInstance;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FleetInstancePluginsTest {

    @TempDir
    Path root;

    @Test
    void installFromCatalogCopiesJar() throws Exception {
        Path catalog = root.resolve("plugins");
        Files.createDirectories(catalog);
        Files.writeString(catalog.resolve("demo.jar"), "jar-bytes");
        FleetInstance lobby = new FleetInstance(
                "lobby", "lobby", "local", "fleet/instances/lobby",
                25567, "127.0.0.1", true, "Lobby");
        Files.createDirectories(root.resolve(lobby.relativeDir()).resolve("plugins"));

        Map<String, Object> installed = FleetInstancePlugins.installFromCatalog(root, lobby, "demo.jar");
        assertTrue(Boolean.TRUE.equals(installed.get("ok")));
        Path dest = root.resolve("fleet/instances/lobby/plugins/demo.jar");
        assertTrue(Files.isRegularFile(dest));
        assertEquals("jar-bytes", Files.readString(dest));

        List<Map<String, Object>> list = FleetInstancePlugins.list(root, lobby);
        assertEquals(1, list.size());
        assertEquals("demo.jar", list.get(0).get("fileName"));
    }
}
