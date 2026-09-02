package com.yapcore.web;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginPermissionScannerTest {

    @Test
    void readsCommandAndPermissionSections() {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        PluginPermissionScanner.collectFromCommands(out, "YaPEssentials", Map.of(
                "spawn", Map.of("permission", "yapessentials.spawn", "description", "Teleport to spawn")));
        PluginPermissionScanner.collectFromPermissions(out, "YaPEssentials", Map.of(
                "yapessentials.admin", Map.of("description", "Reload essentials")));
        assertEquals("/spawn", out.get("yapessentials.spawn").get("label"));
        assertTrue(out.containsKey("yapessentials.admin"));
    }
}
