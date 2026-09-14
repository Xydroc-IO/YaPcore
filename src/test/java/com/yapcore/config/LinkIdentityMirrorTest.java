package com.yapcore.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LinkIdentityMirrorTest {

    @TempDir
    Path tmp;

    @Test
    void mirrorsMotdNotNetworkMaxPlayers() throws Exception {
        Path serverProps = tmp.resolve("config/server.properties");
        Files.createDirectories(serverProps.getParent());
        ServerConfig cfg = ServerConfig.loadOrCreate(serverProps);
        cfg.setMotd("Shared world MOTD");
        cfg.setMaxPlayers(42);
        cfg.setOnlineMode(true);
        cfg.setPublicHost("play.example.com");
        cfg.setPublicPort(25565);
        cfg.setPort(25566);
        cfg.setLinkEmbedHome("link-data");
        cfg.save();

        Path linkDir = tmp.resolve("link-data");
        Files.createDirectories(linkDir);
        Properties seed = new Properties();
        seed.setProperty("max-players", "9000");
        seed.setProperty("motd", "stale");
        try (var out = Files.newOutputStream(linkDir.resolve("link.properties"))) {
            seed.store(out, "seed");
        }

        assertTrue(LinkIdentityMirror.syncFromServerConfig(tmp, cfg));

        Properties link = new Properties();
        try (var in = Files.newInputStream(linkDir.resolve("link.properties"))) {
            link.load(in);
        }
        assertEquals("Shared world MOTD", link.getProperty("motd"));
        assertEquals("9000", link.getProperty("max-players"), "network max must stay on Link");
        assertEquals("true", link.getProperty("online-mode"));
        assertEquals("play.example.com", link.getProperty("public-host"));
        assertEquals("25565", link.getProperty("public-port"));
        // Non-fleet: seed blank backends to Folia listen port (not chassis Via :25566 when Via is on).
        String expectedBackend = "127.0.0.1:" + cfg.foliaListenPort();
        assertEquals(expectedBackend, link.getProperty("servers.lobby"));
        assertEquals(expectedBackend, link.getProperty("bedrock-backend"));
        assertEquals("native", link.getProperty("bedrock-mode"));
        assertEquals("true", link.getProperty("bedrock-enabled"));
        assertEquals("0.0.0.0:25565,0.0.0.0:19132", link.getProperty("bedrock-bind"));

        assertFalse(LinkIdentityMirror.syncFromServerConfig(tmp, cfg));
    }

    @Test
    void fleetEnabledDoesNotStompPerServerBackends() throws Exception {
        Path serverProps = tmp.resolve("config/server.properties");
        Files.createDirectories(serverProps.getParent());
        ServerConfig cfg = ServerConfig.loadOrCreate(serverProps);
        cfg.setFleetEnabled(true);
        cfg.setPort(25566);
        cfg.setLinkEmbedHome("link-data");
        cfg.setMotd("fleet motd");
        cfg.save();

        Path linkDir = tmp.resolve("link-data");
        Files.createDirectories(linkDir);
        Properties seed = new Properties();
        seed.setProperty("servers.lobby", "127.0.0.1:25567");
        seed.setProperty("servers.survival", "127.0.0.1:25568");
        seed.setProperty("bedrock-backend", "127.0.0.1:25567");
        seed.setProperty("motd", "old");
        try (var out = Files.newOutputStream(linkDir.resolve("link.properties"))) {
            seed.store(out, "seed");
        }

        assertTrue(LinkIdentityMirror.syncFromServerConfig(tmp, cfg));

        Properties link = new Properties();
        try (var in = Files.newInputStream(linkDir.resolve("link.properties"))) {
            link.load(in);
        }
        assertEquals("fleet motd", link.getProperty("motd"));
        assertEquals("127.0.0.1:25567", link.getProperty("servers.lobby"));
        assertEquals("127.0.0.1:25568", link.getProperty("servers.survival"));
        assertEquals("127.0.0.1:25567", link.getProperty("bedrock-backend"));
    }
}
