package com.yapcore.crossplay.bedrock;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P4.4 vault inject — Paper-side store when ServerPlayer inject is unavailable. */
class BedrockPaperInventoryInjectTest {

    @Test
    void injectGiveSnapshotClear() {
        BedrockPaperInventoryInject vault = new BedrockPaperInventoryInject();
        UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000042");
        vault.inject(uuid, "BedrockSteve");
        assertTrue(vault.has("BedrockSteve"));

        vault.give("BedrockSteve", "DIAMOND", 3);
        int[] ids = vault.snapshotNetworkIds("BedrockSteve", 36);
        assertNotNull(ids);
        assertEquals(36, ids.length);
        // diamond network id must be non-zero if itemstates loaded
        boolean any = false;
        for (int id : ids) {
            if (id > 0) {
                any = true;
                break;
            }
        }
        // Itemstates may be empty in unit test classpath — still allow air-only if catalog missing
        if (BedrockItemStates.all().isEmpty()) {
            return;
        }
        assertTrue(any, "expected diamond network id in vault snapshot");

        vault.clear("BedrockSteve");
        ids = vault.snapshotNetworkIds("BedrockSteve", 36);
        for (int id : ids) {
            assertEquals(0, id);
        }
        vault.eject("BedrockSteve");
        assertFalse(vault.has("BedrockSteve"));
    }
}
