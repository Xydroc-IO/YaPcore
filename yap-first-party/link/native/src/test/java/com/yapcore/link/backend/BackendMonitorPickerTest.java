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
}
