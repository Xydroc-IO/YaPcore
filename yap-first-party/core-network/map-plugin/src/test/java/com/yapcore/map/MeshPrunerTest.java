package com.yapcore.map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeshPrunerTest {

    @Test
    void prunesByAge(@TempDir Path tempDir) throws Exception {
        Path mesh = tempDir.resolve("world/full/0_0.json");
        Files.createDirectories(mesh.getParent());
        Files.writeString(mesh, "{\"v\":2,\"n\":1,\"d\":[]}");
        Path manifest = tempDir.resolve("world/full/manifest.json");
        Files.writeString(manifest, "{}");
        Files.setLastModifiedTime(mesh, FileTime.from(Instant.now().minusSeconds(10 * 86_400L)));

        MeshPruner.Result result = MeshPruner.prune(tempDir, 7, 0);
        assertEquals(1, result.deletedFiles());
        assertTrue(result.freedBytes() > 0);
        assertTrue(Files.notExists(mesh));
        assertTrue(Files.isRegularFile(manifest));
        assertTrue(result.affectedWorldLayers().contains("world|full"));
    }

    @Test
    void diskBudgetKeepsRecentWhenUnderLimit(@TempDir Path tempDir) throws Exception {
        Path a = tempDir.resolve("world/full/a_0.json");
        Path b = tempDir.resolve("world/full/b_0.json");
        Files.createDirectories(a.getParent());
        byte[] fat = new byte[2048];
        Files.write(a, fat);
        Files.write(b, fat);
        Files.setLastModifiedTime(a, FileTime.from(Instant.now().minusSeconds(100)));
        Files.setLastModifiedTime(b, FileTime.from(Instant.now()));

        MeshPruner.Result keep = MeshPruner.prune(tempDir, 0, 1);
        assertEquals(0, keep.deletedFiles());
        assertTrue(Files.isRegularFile(a));
        assertTrue(Files.isRegularFile(b));
    }
}
