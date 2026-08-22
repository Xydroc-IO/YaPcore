package com.yapcore.link.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LinkTomlLoaderTest {

    @Test
    void parsesServersAndForcedHost(@TempDir Path dir) throws Exception {
        Path toml = dir.resolve("link.toml");
        Files.writeString(toml, """
                bind = "127.0.0.1:25565"
                motd = "Test Link"
                try = ["lobby", "survival"]

                [servers]
                lobby = "127.0.0.1:25566"
                survival = "127.0.0.1:25567"

                [forced-host]
                "lobby.example.com" = "lobby"
                """);
        Properties p = LinkTomlLoader.load(toml);
        assertEquals("127.0.0.1:25565", p.getProperty("bind"));
        assertEquals("lobby, survival", p.getProperty("try").replace("\"", ""));
        assertEquals("127.0.0.1:25566", p.getProperty("servers.lobby"));
        assertEquals("lobby", p.getProperty("forced-host.lobby.example.com"));
    }

    @Test
    void linkConfigLoadsToml(@TempDir Path home) throws Exception {
        Files.writeString(home.resolve("link.toml"), """
                bind = "127.0.0.1:25570"
                [servers]
                lobby = "127.0.0.1:25566"
                try = ["lobby"]
                """);
        var cfg = com.yapcore.link.LinkConfig.load(home);
        assertEquals(25570, cfg.bindPort());
        assertTrue(cfg.servers().containsKey("lobby"));
        assertEquals("lobby", cfg.tryOrder().getFirst());
    }
}
