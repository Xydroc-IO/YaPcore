package com.yapcore.perms;

import org.junit.jupiter.api.Test;

import java.util.Map;

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
}
