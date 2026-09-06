package com.yapcore.conquest;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plugin-jar smoke — API rules live in yap-conquest-api tests. */
class ConquestSmokeTest {

    @Test
    void pluginYmlMainCommandsAndPermissions() throws Exception {
        try (InputStream in = ConquestSmokeTest.class.getResourceAsStream("/plugin.yml")) {
            assertNotNull(in);
            String yml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(yml.contains("main: com.yapcore.conquest.ConquestPlugin"));
            assertTrue(yml.contains("folia-supported: true"));
            assertTrue(yml.contains("yapconquest.use"));
            assertTrue(yml.contains("yapconquest.admin"));
            assertTrue(yml.contains("depend: [YaPDB]"));
            assertTrue(yml.contains("softdepend: [YaPFactions]"));
        }
    }

    @Test
    void defaultConfigOptInAndSafeToggles() throws Exception {
        try (InputStream in = ConquestSmokeTest.class.getResourceAsStream("/config.yml")) {
            assertNotNull(in);
            String yml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(yml.contains("enabled: false"));
            assertTrue(yml.contains("claim-cost: 1"));
            assertTrue(yml.contains("require-enemy: true"));
            assertTrue(yml.contains("duration-seconds: 15"));
            assertTrue(yml.contains("only-own-territory: true"));
            assertTrue(yml.contains("claimed: deny"));
        }
    }

    @Test
    void defaultConfigZoneAndRelationSafety() throws Exception {
        try (InputStream in = ConquestSmokeTest.class.getResourceAsStream("/config.yml")) {
            assertNotNull(in);
            String yml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(yml.contains("allies-can-build: true"));
            assertTrue(yml.contains("enemy-pvp-only: true"));
            assertTrue(yml.contains("blocks-pvp: true"));
            assertTrue(yml.contains("warzone-worlds: []"));
            assertTrue(yml.contains("safezone-worlds: []"));
            assertTrue(yml.contains("Entering Warzone"));
        }
    }
}
