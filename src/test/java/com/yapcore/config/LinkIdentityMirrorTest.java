package com.yapcore.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LinkIdentityMirrorTest {

    @TempDir
    Path tmp;

    @Test
    void mirrorsSharedIdentityOntoLinkProperties() throws Exception {
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

        LinkIdentityMirror.syncFromServerConfig(tmp, cfg);

        Properties link = new Properties();
        try (var in = Files.newInputStream(tmp.resolve("link-data/link.properties"))) {
            link.load(in);
        }
        assertEquals("Shared world MOTD", link.getProperty("motd"));
        assertEquals("42", link.getProperty("max-players"));
        assertEquals("true", link.getProperty("online-mode"));
        assertEquals("play.example.com", link.getProperty("public-host"));
        assertEquals("25565", link.getProperty("public-port"));
        assertEquals("127.0.0.1:25566", link.getProperty("servers.lobby"));
        assertEquals("127.0.0.1:25566", link.getProperty("bedrock-backend"));
        assertEquals("native", link.getProperty("bedrock-mode"));
        assertEquals("true", link.getProperty("bedrock-enabled"));
        assertEquals("0.0.0.0:25565,0.0.0.0:19132", link.getProperty("bedrock-bind"));
    }
}
