package com.yapcore.protect;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProtectConfigServerIdTest {

    @Test
    void fleetStampWinsOverCopiedLobbySeed() {
        assertEquals("survival", ProtectConfig.resolveServerId("lobby", "survival\n"));
        assertEquals("creative", ProtectConfig.resolveServerId("lobby", "creative"));
    }

    @Test
    void fallsBackToConfigured() {
        assertEquals("lobby", ProtectConfig.resolveServerId("lobby", null));
        assertEquals("lobby", ProtectConfig.resolveServerId("lobby", "  \n# comment"));
    }

    @Test
    void hintPathIsInstanceRoot() {
        Path hint = ProtectConfig.instanceServerIdHint(Path.of("/fleet/survival/plugins/YaPProtect"));
        assertEquals(Path.of("/fleet/survival/yap-server-id.txt"), hint);
    }
}
