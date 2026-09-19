package com.yapcore.npcs;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class NpcsConfigHintTest {

    @Test
    void relativePluginDataFolderStillFindsInstanceHint() {
        Path hint = NpcsConfig.instanceServerIdHint(Path.of("plugins/YaPNpcs"));
        assertNotNull(hint);
        assertEquals("yap-server-id.txt", hint.getFileName().toString());
        assertEquals(Path.of(".").toAbsolutePath().normalize(), hint.getParent());
    }
}
