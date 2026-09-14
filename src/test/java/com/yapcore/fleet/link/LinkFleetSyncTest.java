package com.yapcore.fleet.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yapcore.config.ServerConfig;
import com.yapcore.fleet.model.FleetInstance;
import com.yapcore.fleet.store.FleetStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LinkFleetSyncTest {

    @TempDir
    Path tmp;

    @Test
    void syncWritesServersAndTryOrder() throws Exception {
        Path cfgFile = tmp.resolve("config/server.properties");
        Files.createDirectories(cfgFile.getParent());
        ServerConfig cfg = ServerConfig.loadOrCreate(cfgFile);
        cfg.setLinkEmbedHome("link-data");
        cfg.save();

        FleetStore store = new FleetStore(tmp);
        store.putInstance(FleetInstance.lobby(25566));
        store.putInstance(new FleetInstance(
                "survival", "survival", "local", "fleet/instances/survival",
                25567, "127.0.0.1", false, "Survival"));
        LinkFleetSync.syncAll(tmp, cfg, store);

        Path propsFile = tmp.resolve("link-data/link.properties");
        assertTrue(Files.isRegularFile(propsFile));
        Properties p = new Properties();
        try (var in = Files.newInputStream(propsFile)) {
            p.load(in);
        }
        assertEquals("127.0.0.1:25566", p.getProperty("servers.lobby"));
        assertEquals("127.0.0.1:25567", p.getProperty("servers.survival"));
        assertEquals("lobby,survival", p.getProperty("try"));
        assertEquals("127.0.0.1:25566", p.getProperty("bedrock-backend"));
        assertEquals("true", p.getProperty("aggregate-player-count"));
        assertEquals("true", p.getProperty("skip-down-on-forced-host"));
        assertEquals(2, LinkFleetSync.readServers(p).size());
        assertEquals("lobby", LinkFleetSync.readTryOrder(p).get(0));
    }

    @Test
    void syncSumsMaxPlayersAcrossInstances() throws Exception {
        Path cfgFile = tmp.resolve("config/server.properties");
        Files.createDirectories(cfgFile.getParent());
        ServerConfig cfg = ServerConfig.loadOrCreate(cfgFile);
        cfg.setLinkEmbedHome("link-data");
        cfg.save();

        FleetStore store = new FleetStore(tmp);
        store.putInstance(FleetInstance.lobby(25566));
        store.putInstance(new FleetInstance(
                "survival", "survival", "local", "fleet/instances/survival",
                25567, "127.0.0.1", false, "Survival"));

        writeMaxPlayers(tmp.resolve("fleet/instances/lobby/server.properties"), 250);
        writeMaxPlayers(tmp.resolve("fleet/instances/survival/server.properties"), 250);

        LinkFleetSync.syncAll(tmp, cfg, store);

        Properties p = new Properties();
        try (var in = Files.newInputStream(tmp.resolve("link-data/link.properties"))) {
            p.load(in);
        }
        assertEquals("500", p.getProperty("max-players"));
    }

    private static void writeMaxPlayers(Path file, int max) throws Exception {
        Files.createDirectories(file.getParent());
        Properties p = new Properties();
        p.setProperty("max-players", Integer.toString(max));
        try (var out = Files.newOutputStream(file)) {
            p.store(out, "test");
        }
    }
}
