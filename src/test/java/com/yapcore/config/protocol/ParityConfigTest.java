package com.yapcore.config.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yapcore.config.ServerConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ParityConfigTest {

    @TempDir
    Path tmp;

    @Test
    void defaultsParityOffAndBand2650() throws Exception {
        Path file = tmp.resolve("server.properties");
        Files.writeString(file, "port=25566\n");
        ServerConfig cfg = ServerConfig.loadOrCreate(file);
        assertFalse(cfg.isParityBedrockFeel());
        assertEquals("band_26_50", cfg.getParityBedrockBand());
    }

    @Test
    void canEnableParityFeelAndBand() throws Exception {
        Path file = tmp.resolve("server.properties");
        Files.writeString(file, """
                port=25566
                parity.bedrock-feel=true
                parity.bedrock-band=band_26_50
                """);
        ServerConfig cfg = ServerConfig.loadOrCreate(file);
        assertTrue(cfg.isParityBedrockFeel());
        assertEquals("band_26_50", cfg.getParityBedrockBand());
        cfg.setParityBedrockFeel(false);
        assertFalse(cfg.isParityBedrockFeel());
    }

    @Test
    void applyDefaultsIncludesParityKeys() {
        Properties p = new Properties();
        ProtocolEdgeConfig.applyDefaults(p);
        assertEquals("false", p.getProperty("parity.bedrock-feel"));
        assertEquals("band_26_50", p.getProperty("parity.bedrock-band"));
    }
}
