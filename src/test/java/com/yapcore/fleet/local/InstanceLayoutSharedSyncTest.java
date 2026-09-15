package com.yapcore.fleet.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class InstanceLayoutSharedSyncTest {

    @TempDir
    Path root;

    @Test
    void syncOverwritesSharedDefsButLeavesServerIdAlone() throws Exception {
        Path catalogItems = root.resolve("plugins/YaPItems/items/custom");
        Files.createDirectories(catalogItems);
        Files.writeString(catalogItems.resolve("god_killer.yml"), "id: god_killer\nv: 2\n");
        Files.createDirectories(root.resolve("plugins/YaPPlayerData"));
        Files.writeString(root.resolve("plugins/YaPPlayerData/kits.yml"), "kits:\n  vip:\n    cost: 0\n");
        Files.createDirectories(root.resolve("plugins/YaPDB"));
        Files.writeString(root.resolve("plugins/YaPDB/config.yml"), "jdbc:\n  url: jdbc:mysql://shared/db\n");

        Path lobby = root.resolve("fleet/instances/lobby");
        Path survival = root.resolve("fleet/instances/survival");
        for (Path inst : new Path[] {lobby, survival}) {
            Path items = inst.resolve("plugins/YaPItems/items/custom");
            Files.createDirectories(items);
            Files.writeString(items.resolve("god_killer.yml"), "id: god_killer\nv: 1\n");
            Path pd = inst.resolve("plugins/YaPPlayerData");
            Files.createDirectories(pd);
            Files.writeString(pd.resolve("kits.yml"), "kits: {}\n");
            Files.writeString(pd.resolve("config.yml"), "server-id: " + inst.getFileName() + "\n");
            Files.createDirectories(inst.resolve("plugins/YaPDB"));
            Files.writeString(inst.resolve("plugins/YaPDB/config.yml"), "jdbc:\n  url: jdbc:sqlite:local.db\n");
        }

        int written = InstanceLayout.syncSharedCatalogDataToLocalFleet(root);
        assertTrue(written >= 4, "expected kits+items+jdbc updates across instances, got " + written);

        assertEquals("id: god_killer\nv: 2\n",
                Files.readString(lobby.resolve("plugins/YaPItems/items/custom/god_killer.yml")));
        assertEquals("id: god_killer\nv: 2\n",
                Files.readString(survival.resolve("plugins/YaPItems/items/custom/god_killer.yml")));
        assertEquals("kits:\n  vip:\n    cost: 0\n",
                Files.readString(lobby.resolve("plugins/YaPPlayerData/kits.yml")));
        assertEquals("jdbc:\n  url: jdbc:mysql://shared/db\n",
                Files.readString(survival.resolve("plugins/YaPDB/config.yml")));
        assertEquals("server-id: lobby\n",
                Files.readString(lobby.resolve("plugins/YaPPlayerData/config.yml")),
                "server-id must stay per-instance");
        assertEquals("server-id: survival\n",
                Files.readString(survival.resolve("plugins/YaPPlayerData/config.yml")));
    }

    @Test
    void syncIsIdempotentWhenAlreadyAligned() throws Exception {
        Path custom = root.resolve("plugins/YaPItems/items/custom");
        Files.createDirectories(custom);
        byte[] bytes = "id: x\n".getBytes(StandardCharsets.UTF_8);
        Files.write(custom.resolve("x.yml"), bytes);
        Path dest = root.resolve("fleet/instances/lobby/plugins/YaPItems/items/custom");
        Files.createDirectories(dest);
        Files.write(dest.resolve("x.yml"), bytes);

        assertEquals(0, InstanceLayout.syncSharedCatalogData(root, root.resolve("fleet/instances/lobby")));
    }
}
