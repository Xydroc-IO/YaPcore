package com.yapcore.link.backend;

import com.yapcore.link.LinkConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class BackendMonitorPickerTest {

    @Test
    void pickLoginTargetSkipsDownWhenConfigured() throws Exception {
        LinkConfig cfg = LinkConfig.load(java.nio.file.Files.createTempDirectory("link-cfg"));
        BackendMonitor mon = new BackendMonitor(cfg);
        // No probes run — pick falls back to resolveTry
        LinkConfig.Backend picked = mon.pickLoginTarget(null);
        assertNotNull(picked);
        assertEquals("lobby", picked.name());
    }

    @Test
    void pickFallbackSkipsLostBackendAndUsesTryOrder() throws Exception {
        java.nio.file.Path home = java.nio.file.Files.createTempDirectory("link-cfg-fb");
        java.nio.file.Files.writeString(home.resolve("link.properties"), """
                servers.lobby=127.0.0.1:25566
                servers.survival=127.0.0.1:25567
                try=lobby
                fallback-on-backend-loss=true
                """);
        // forwarding.secret is created by LinkConfig.load
        LinkConfig cfg = LinkConfig.load(home);
        BackendMonitor mon = new BackendMonitor(cfg);
        // pickFallback returns try candidates even when probe says DOWN so soft-switch can retry.
        LinkConfig.Backend hub = mon.pickFallback("survival");
        assertNotNull(hub);
        assertEquals("lobby", hub.name());
        org.junit.jupiter.api.Assertions.assertNull(mon.pickFallback("lobby"));
    }

    @Test
    void applyMaxCeilingUsesLiveSumNotConfiguredFloor() {
        assertEquals(250, BackendMonitor.applyMaxCeiling(250, 500));
        assertEquals(500, BackendMonitor.applyMaxCeiling(500, 500));
        assertEquals(400, BackendMonitor.applyMaxCeiling(600, 400));
        assertEquals(0, BackendMonitor.applyMaxCeiling(0, 500));
    }
}
