package com.yapcore.perms;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionNodesTest {

    @Test
    void wildcardGrant() {
        Map<String, Boolean> nodes = Map.of("yapdata.kit.*", true);
        assertTrue(PermissionNodes.has(nodes, "yapdata.kit.starter"));
        assertFalse(PermissionNodes.has(nodes, "yapdata.home"));
    }

    @Test
    void exactDenyOverridesMissing() {
        Map<String, Boolean> nodes = Map.of("yapdata.home", false);
        assertFalse(PermissionNodes.has(nodes, "yapdata.home"));
    }

    @Test
    void exactDenyBeatsWildcardGrant() {
        Map<String, Boolean> nodes = Map.of(
                "yapdata.kit.*", true,
                "yapdata.kit.vip", false);
        assertFalse(PermissionNodes.has(nodes, "yapdata.kit.vip"));
        assertTrue(PermissionNodes.has(nodes, "yapdata.kit.starter"));
    }

    @Test
    void moreSpecificWildcardWins() {
        Map<String, Boolean> nodes = Map.of(
                "yapdata.*", true,
                "yapdata.kit.*", false);
        assertFalse(PermissionNodes.has(nodes, "yapdata.kit.starter"));
        assertTrue(PermissionNodes.has(nodes, "yapdata.home"));
    }

    @Test
    void rootStarGrant() {
        assertTrue(PermissionNodes.has(Map.of("*", true), "anything.here"));
    }

    @Test
    void wildcardNegation() {
        Map<String, Boolean> nodes = Map.of("essentials.fly.*", false);
        assertFalse(PermissionNodes.has(nodes, "essentials.fly.others"));
    }

    @Test
    void decidingPatternIsMostSpecific() {
        Map<String, Boolean> nodes = Map.of(
                "*", true,
                "yapdata.kit.*", false);
        assertEquals("yapdata.kit.*", PermissionNodes.decidingPattern(nodes, "yapdata.kit.vip"));
    }
}
