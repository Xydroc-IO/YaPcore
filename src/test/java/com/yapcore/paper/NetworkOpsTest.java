package com.yapcore.paper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NetworkOpsTest {

    @TempDir
    Path temp;

    @Test
    void addNameAndUuidRoundTrip() throws Exception {
        Files.createDirectories(temp.resolve("config"));
        Files.writeString(temp.resolve("config/server.properties"), "ops=\n");

        NetworkOps.addName(temp, "MadHatter");
        assertTrue(NetworkOps.containsName(temp, "madhatter"));
        assertTrue(NetworkOps.isNetworkOp(temp, PaperOps.offlineUuid("MadHatter"), "MadHatter"));

        UUID online = UUID.fromString("22222222-2222-2222-2222-222222222222");
        NetworkOps.add(temp, online, "MadHatter");
        assertTrue(NetworkOps.containsUuid(temp, online));
        assertFalse(NetworkOps.containsUuid(temp, PaperOps.offlineUuid("MadHatter")));

        NetworkOps.syncFromChassisOps(temp, List.of("Other"));
        assertTrue(NetworkOps.containsName(temp, "Other"));

        NetworkOps.removeName(temp, "MadHatter");
        assertFalse(NetworkOps.containsName(temp, "MadHatter"));
    }
}
