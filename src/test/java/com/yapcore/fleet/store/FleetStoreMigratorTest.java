package com.yapcore.fleet.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yapcore.config.ServerConfig;
import com.yapcore.fleet.model.FleetInstance;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FleetStoreMigratorTest {

    @TempDir
    Path tmp;

    @Test
    void storeRoundTripAndPortAllocation() throws Exception {
        FleetStore store = new FleetStore(tmp);
        store.putInstance(FleetInstance.lobby(25566));
        store.putInstance(new FleetInstance(
                "survival", "survival", "local", "fleet/instances/survival",
                25567, "127.0.0.1", false, "Survival"));
        assertTrue(store.exists());
        FleetStore loaded = new FleetStore(tmp);
        loaded.load();
        assertEquals(2, loaded.instances().size());
        assertEquals(25568, loaded.nextFreePort(25566));
        assertTrue(loaded.portUsed(25566));
        assertFalse(loaded.portUsed(25570));
    }

    @Test
    void migratorIsIdempotent() throws Exception {
        Path legacy = tmp.resolve("folia-kernel");
        Files.createDirectories(legacy.resolve("plugins"));
        Files.writeString(legacy.resolve("eula.txt"), "eula=true\n");
        Path cfgFile = tmp.resolve("config/server.properties");
        Files.createDirectories(cfgFile.getParent());
        ServerConfig cfg = ServerConfig.loadOrCreate(cfgFile);
        cfg.setFoliaDir("folia-kernel");
        cfg.save();

        FleetStore store = new FleetStore(tmp);
        FleetMigrator migrator = new FleetMigrator(tmp, cfg, store);
        assertTrue(migrator.enableFleet());
        assertTrue(Files.isDirectory(tmp.resolve("fleet/instances/lobby")));
        assertTrue(cfg.isFleetEnabled());
        assertEquals("fleet/instances/lobby", cfg.getFoliaDir());

        // Second enable should not throw
        assertTrue(migrator.enableFleet());
        FleetStore again = new FleetStore(tmp);
        again.load();
        assertEquals(1, again.instances().size());
        assertEquals("lobby", again.primaryId());
    }
}
