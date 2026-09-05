package com.yapcore.discord;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleChannelRulesTest {

    @Test
    void emptyWhitelistAllowsAll() {
        assertTrue(ConsoleChannelRules.isCommandAllowed("stop", List.of()));
        assertTrue(ConsoleChannelRules.isCommandAllowed("tps", List.of()));
        assertFalse(ConsoleChannelRules.isCommandAllowed("", List.of()));
        assertFalse(ConsoleChannelRules.isCommandAllowed("  ", List.of("tps")));
    }

    @Test
    void nonEmptyWhitelistChecksFirstToken() {
        assertTrue(ConsoleChannelRules.isCommandAllowed("tps", List.of("tps", "list")));
        assertTrue(ConsoleChannelRules.isCommandAllowed("list uuids", List.of("tps", "list")));
        assertFalse(ConsoleChannelRules.isCommandAllowed("stop", List.of("tps", "list")));
    }

    @Test
    void outboundFilterMatchesWarnAlias() {
        assertTrue(ConsoleChannelRules.levelAllowed(Level.INFO, List.of("INFO", "WARN", "SEVERE")));
        assertTrue(ConsoleChannelRules.levelAllowed(Level.WARNING, List.of("INFO", "WARN", "SEVERE")));
        assertTrue(ConsoleChannelRules.levelAllowed(Level.SEVERE, List.of("INFO", "WARN", "SEVERE")));
        assertFalse(ConsoleChannelRules.levelAllowed(Level.FINE, List.of("INFO", "WARN", "SEVERE")));
        assertFalse(ConsoleChannelRules.levelAllowed(Level.INFO, List.of()));
    }
}
