package com.yapcore.link.bedrock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FailoverSpawnArrivalTest {

    @TempDir
    Path temp;

    @Test
    void marksSpawnPendingMatchingPortalArrivalFormat() throws Exception {
        Path plugins = temp.resolve("plugins");
        Files.createDirectories(plugins);
        Path linkHome = temp.resolve("link-data");
        Files.createDirectories(linkHome);

        UUID id = UUID.fromString("11111111-2222-3333-4444-555555555555");
        FailoverSpawnArrival.mark(linkHome, id, "Lobby");

        Path file = temp.resolve("plugins").resolve("YaPPortals")
                .resolve("pending-spawn").resolve(id.toString());
        assertTrue(Files.isRegularFile(file));
        String[] parts = Files.readString(file).trim().split("\\R");
        assertEquals(3, parts.length);
        assertEquals("lobby", parts[0]);
        assertEquals("spawn", parts[1]);
        Long.parseLong(parts[2]);
    }

    @Test
    void markIfAbsentDoesNotOverwriteExistingPending() throws Exception {
        Path plugins = temp.resolve("plugins");
        Files.createDirectories(plugins);
        Path linkHome = temp.resolve("link-data");
        Files.createDirectories(linkHome);

        UUID id = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        Path dir = temp.resolve("plugins").resolve("YaPPortals").resolve("pending-spawn");
        Files.createDirectories(dir);
        Path file = dir.resolve(id.toString());
        Files.writeString(file, "skyblock\nisland\n1\n");

        FailoverSpawnArrival.markIfAbsent(linkHome, id, "lobby");

        String body = Files.readString(file).trim();
        assertTrue(body.startsWith("skyblock\nisland"), body);
    }

    @Test
    void markIfAbsentWritesWhenMissing() throws Exception {
        Path plugins = temp.resolve("plugins");
        Files.createDirectories(plugins);
        Path linkHome = temp.resolve("link-data");
        Files.createDirectories(linkHome);

        UUID id = UUID.fromString("99999999-8888-7777-6666-555555555555");
        FailoverSpawnArrival.markIfAbsent(linkHome, id, "creative");

        Path file = temp.resolve("plugins").resolve("YaPPortals")
                .resolve("pending-spawn").resolve(id.toString());
        assertTrue(Files.isRegularFile(file));
        assertEquals("creative", Files.readString(file).trim().split("\\R")[0]);
    }

    @Test
    void findYapRootFromLinkDataParent() {
        Path linkHome = temp.resolve("link-data");
        assertTrue(FailoverSpawnArrival.findYapRoot(linkHome).isEmpty());
    }
}
