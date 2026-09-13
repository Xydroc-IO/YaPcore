package com.yapcore.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BedrockModeApplierTest {

    @TempDir
    Path tmp;

    @Test
    void firstPartyEnablesLinkAndChassisBedrock() throws Exception {
        Path linkHome = tmp.resolve("link-data");
        Path serverProps = tmp.resolve("config/server.properties");
        Files.createDirectories(serverProps.getParent());
        Map<String, Object> out = BedrockModeApplier.apply(tmp, linkHome, serverProps, "first-party");
        assertEquals("forwarder", out.get("bedrockMode"));
        assertEquals(true, out.get("linkBedrockEnabled"));
        assertEquals(false, out.get("linkBedrockNative"));
        assertEquals(true, out.get("chassisBedrockEnabled"));
        Properties link = load(linkHome.resolve("link.properties"));
        assertEquals("true", link.getProperty("bedrock-enabled"));
        assertEquals("forwarder", link.getProperty("bedrock-mode"));
        assertEquals("false", link.getProperty("geyser-enabled"));
        Properties server = load(serverProps);
        assertEquals("true", server.getProperty("bedrock-enabled"));
        assertEquals("true", server.getProperty("crossplay-enabled"));
    }

    @Test
    void nativeEnablesLinkOwnsBedrockChassisOff() throws Exception {
        Path linkHome = tmp.resolve("link-data");
        Path serverProps = tmp.resolve("config/server.properties");
        Files.createDirectories(serverProps.getParent());
        Map<String, Object> out = BedrockModeApplier.apply(tmp, linkHome, serverProps, "native");
        assertEquals("native", out.get("bedrockMode"));
        assertEquals(true, out.get("linkBedrockEnabled"));
        assertEquals(true, out.get("linkBedrockNative"));
        assertEquals(false, out.get("chassisBedrockEnabled"));
        Properties link = load(linkHome.resolve("link.properties"));
        assertEquals("true", link.getProperty("bedrock-enabled"));
        assertEquals("native", link.getProperty("bedrock-mode"));
        Properties server = load(serverProps);
        assertEquals("false", server.getProperty("bedrock-enabled"));
        assertEquals("true", server.getProperty("crossplay-enabled"));
    }

    @Test
    void geyserBackupDisablesForwarderAndChassis() throws Exception {
        Path linkHome = tmp.resolve("link-data");
        Path serverProps = tmp.resolve("config/server.properties");
        Files.createDirectories(serverProps.getParent());
        Map<String, Object> out = BedrockModeApplier.apply(tmp, linkHome, serverProps, "geyser-backup");
        assertEquals("geyser-backup", out.get("bedrockMode"));
        assertFalse((Boolean) out.get("linkBedrockEnabled"));
        assertTrue((Boolean) out.get("geyserEnabled"));
        Properties link = load(linkHome.resolve("link.properties"));
        assertEquals("false", link.getProperty("bedrock-enabled"));
        assertEquals("true", link.getProperty("geyser-enabled"));
        Properties server = load(serverProps);
        assertEquals("false", server.getProperty("bedrock-enabled"));
        assertEquals("false", server.getProperty("crossplay-enabled"));
        assertTrue(out.containsKey("warning"));
    }

    @Test
    void blankNormalizesToNative() {
        assertEquals("native", BedrockModeApplier.normalize(null));
        assertEquals("native", BedrockModeApplier.normalize(""));
        assertEquals("native", BedrockModeApplier.normalize("  "));
        assertEquals("forwarder", BedrockModeApplier.normalize("first-party"));
        assertEquals("forwarder", BedrockModeApplier.normalize("forwarder"));
    }

    private static Properties load(Path file) throws Exception {
        Properties p = new Properties();
        try (var in = Files.newInputStream(file)) {
            p.load(in);
        }
        return p;
    }
}
