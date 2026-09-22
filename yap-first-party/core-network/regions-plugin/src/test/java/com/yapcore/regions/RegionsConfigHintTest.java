package com.yapcore.regions;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class RegionsConfigHintTest {

    @Test
    void fleetHintOverridesSeedDefault() {
        assertEquals("lobby", RegionsConfig.resolveServerId("default", "lobby\n"));
    }

    @Test
    void fleetHintOverridesCopiedWrongBackend() {
        assertEquals("survival", RegionsConfig.resolveServerId("lobby", "survival"));
    }

    @Test
    void blankHintKeepsYaml() {
        assertEquals("creative", RegionsConfig.resolveServerId("creative", "  "));
        assertEquals("default", RegionsConfig.resolveServerId(null, null));
    }

    @Test
    void relativePluginDataFolderStillFindsInstanceHint() {
        Path hint = RegionsConfig.instanceServerIdHint(Path.of("plugins/YaPRegions"));
        assertNotNull(hint);
        assertEquals("yap-server-id.txt", hint.getFileName().toString());
        assertEquals(Path.of(".").toAbsolutePath().normalize(), hint.getParent());
    }
}
