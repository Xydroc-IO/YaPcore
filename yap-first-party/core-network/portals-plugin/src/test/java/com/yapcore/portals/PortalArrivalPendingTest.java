package com.yapcore.portals;

import com.yapcore.portals.store.PortalArrivalPending;
import com.yapcore.portals.store.PortalCatalogMirror;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortalArrivalPendingTest {

    @TempDir
    Path temp;

    @Test
    void consumeLeavesFileWhenServerIdMismatch() throws Exception {
        Path instance = temp.resolve("fleet/instances/lobby/plugins/YaPPortals");
        Files.createDirectories(instance);
        PortalCatalogMirror mirror = new PortalCatalogMirror(instance, "lobby", Logger.getGlobal());
        PortalArrivalPending pending = new PortalArrivalPending(mirror, Logger.getGlobal());
        UUID id = UUID.randomUUID();
        pending.mark(id, "survival", PortalArrival.SPAWN);

        Path file = temp.resolve("plugins/YaPPortals/pending-spawn").resolve(id.toString());
        assertTrue(Files.isRegularFile(file), "mark should write shared pending file");

        assertTrue(pending.consume(id, "lobby").isEmpty());
        assertTrue(Files.isRegularFile(file), "wrong backend must not delete pending");

        var req = pending.consume(id, "survival");
        assertTrue(req.isPresent());
        assertEquals(PortalArrival.SPAWN, req.get().arrival());
        assertFalse(Files.isRegularFile(file), "matching backend consumes the file");
    }
}
