package com.yapcore.regions;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionsConfigSafeServerTest {

    @Test
    void lobbyIsSafeByDefault() {
        RegionsConfig lobby = new RegionsConfig("lobby");
        assertTrue(lobby.isSafeServer());
        assertTrue(lobby.safeServers().contains("lobby"));
    }

    @Test
    void survivalIsNotSafeByDefault() {
        RegionsConfig survival = new RegionsConfig("survival");
        assertFalse(survival.isSafeServer());
    }

    @Test
    void applySafeServersForTestOverrides() {
        RegionsConfig config = new RegionsConfig("hub");
        assertTrue(config.isSafeServer());
        config.applySafeServersForTest(Set.of("lobby"));
        assertFalse(config.isSafeServer());
        config.applySafeServersForTest(Set.of("hub", "lobby"));
        assertTrue(config.isSafeServer());
        config.applySafeServersForTest(Set.of());
        assertFalse(config.isSafeServer());
    }

    @Test
    void hubIsSafeByDefault() {
        assertTrue(new RegionsConfig("hub").isSafeServer());
    }
}
