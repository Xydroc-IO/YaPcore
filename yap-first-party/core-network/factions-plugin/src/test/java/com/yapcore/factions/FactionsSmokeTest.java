package com.yapcore.factions;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plugin-jar smoke beyond {@link com.yapcore.factions.chat.FactionChatStateTest}.
 * Domain rules live in yap-factions-api tests.
 */
class FactionsSmokeTest {

    @Test
    void pluginYmlMainCommandsAndPermissions() throws Exception {
        try (InputStream in = FactionsSmokeTest.class.getResourceAsStream("/plugin.yml")) {
            assertNotNull(in);
            String yml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(yml.contains("main: com.yapcore.factions.FactionsPlugin"));
            assertTrue(yml.contains("folia-supported: true"));
            assertTrue(yml.contains("yapfactions.use"));
            assertTrue(yml.contains("yapfactions.admin"));
            assertTrue(yml.contains("yapfactions.create"));
            assertTrue(yml.contains("depend: [YaPDB]"));
        }
    }

    @Test
    void defaultConfigOptInPowerAndShield() throws Exception {
        try (InputStream in = FactionsSmokeTest.class.getResourceAsStream("/config.yml")) {
            assertNotNull(in);
            String yml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(yml.contains("enabled: false"));
            assertTrue(yml.contains("base-max: 50"));
            assertTrue(yml.contains("per-member: 10"));
            assertTrue(yml.contains("loss-on-death: 2"));
            assertTrue(yml.contains("seconds: 3600"));
            assertTrue(yml.contains("blocks-pvp: true"));
        }
    }

    @Test
    void defaultConfigLabelsAndTerritoryMessages() throws Exception {
        try (InputStream in = FactionsSmokeTest.class.getResourceAsStream("/config.yml")) {
            assertNotNull(in);
            String yml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(yml.contains("singular: Faction"));
            assertTrue(yml.contains("plural: Factions"));
            assertTrue(yml.contains("command: f"));
            assertTrue(yml.contains("%faction%"));
            assertTrue(yml.contains("members-can-open-chests: true"));
        }
    }
}
