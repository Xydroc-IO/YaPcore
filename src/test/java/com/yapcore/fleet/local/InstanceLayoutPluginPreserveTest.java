package com.yapcore.fleet.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yapcore.config.ServerConfig;
import com.yapcore.fleet.model.FleetInstance;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class InstanceLayoutPluginPreserveTest {

    @TempDir
    Path root;

    @Test
    void seedDoesNotRemoveOrOverwriteInstalledPlugins() throws Exception {
        Path catalog = root.resolve("plugins");
        Files.createDirectories(catalog);
        Files.writeString(catalog.resolve("yap-db.jar"), "catalog-db-v2", StandardCharsets.UTF_8);
        Files.writeString(catalog.resolve("yap-items.jar"), "catalog-items", StandardCharsets.UTF_8);
        Files.writeString(catalog.resolve("yap-guard.jar"), "catalog-guard", StandardCharsets.UTF_8);

        Path instanceDir = root.resolve("fleet/instances/survival");
        Path plugins = instanceDir.resolve("plugins");
        Files.createDirectories(plugins);
        Files.writeString(plugins.resolve("grim.jar"), "operator-grim", StandardCharsets.UTF_8);
        Files.writeString(plugins.resolve("yap-disasters.jar"), "operator-disasters", StandardCharsets.UTF_8);
        Files.writeString(plugins.resolve("yap-db.jar"), "instance-db-v1", StandardCharsets.UTF_8);
        Files.writeString(plugins.resolve("yap-guard.jar.disabled"), "hard-disabled-guard", StandardCharsets.UTF_8);
        Files.createDirectories(plugins.resolve("GrimAC"));
        Files.writeString(plugins.resolve("GrimAC/config.yml"), "custom: true\n", StandardCharsets.UTF_8);

        InstanceLayout.ensureInstancePluginsDir(instanceDir);
        InstanceLayout.seedPluginsFromRoot(root, instanceDir);

        assertEquals("operator-grim", Files.readString(plugins.resolve("grim.jar")));
        assertEquals("operator-disasters", Files.readString(plugins.resolve("yap-disasters.jar")));
        assertEquals("instance-db-v1", Files.readString(plugins.resolve("yap-db.jar")),
                "must not overwrite existing jar from catalog");
        assertTrue(Files.isRegularFile(plugins.resolve("yap-guard.jar.disabled")));
        assertFalse(Files.isRegularFile(plugins.resolve("yap-guard.jar")),
                "must not re-seed over a hard-disabled default");
        assertEquals("custom: true\n", Files.readString(plugins.resolve("GrimAC/config.yml")));
        assertTrue(Files.isRegularFile(plugins.resolve("yap-items.jar")),
                "missing defaults may still be seeded");
    }

    @Test
    void countAndHealRespectExistingHardDisabledJars() throws Exception {
        FleetInstance inst = new FleetInstance(
                "lobby", "lobby", "local", "fleet/instances/lobby",
                25567, "127.0.0.1", true, "Lobby");
        Path plugins = InstanceLayout.dir(root, inst).resolve("plugins");
        Files.createDirectories(plugins);
        Files.writeString(plugins.resolve("tebex.jar.disabled"), "x", StandardCharsets.UTF_8);

        Path cfgFile = root.resolve("config/server.properties");
        Files.createDirectories(cfgFile.getParent());
        Files.writeString(cfgFile, "port=25566\n", StandardCharsets.UTF_8);
        ServerConfig config = new ServerConfig(cfgFile);
        config.load();

        assertEquals(1, InstanceLayout.countPluginJars(root, inst));
        assertEquals(0, InstanceLayout.healEmptyPlugins(root, config, inst),
                "heal must not run when hard-disabled jars are present");
        assertTrue(Files.isRegularFile(plugins.resolve("tebex.jar.disabled")));
    }
}
