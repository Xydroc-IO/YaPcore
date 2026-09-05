package com.yapcore.map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TilePrunerTest {

    @Test
    void prunesByAge(@TempDir Path tempDir) throws Exception {
        Path tile = tempDir.resolve("world/surface/0/0_0.png");
        Files.createDirectories(tile.getParent());
        Files.write(tile, new byte[] {1, 2, 3, 4});
        Files.setLastModifiedTime(tile, FileTime.from(Instant.now().minusSeconds(10 * 86_400L)));

        TilePruner.Result result = TilePruner.prune(tempDir, 7, 0);
        assertEquals(1, result.deletedFiles());
        assertTrue(result.freedBytes() >= 4);
        assertTrue(Files.notExists(tile));
    }

    @Test
    void diskBudgetKeepsRecentWhenUnderLimit(@TempDir Path tempDir) throws Exception {
        Path a = tempDir.resolve("a.png");
        Path b = tempDir.resolve("b.png");
        byte[] fat = new byte[2048];
        Files.write(a, fat);
        Files.write(b, fat);
        Files.setLastModifiedTime(a, FileTime.from(Instant.now().minusSeconds(100)));
        Files.setLastModifiedTime(b, FileTime.from(Instant.now()));

        TilePruner.Result keep = TilePruner.prune(tempDir, 0, 1);
        assertEquals(0, keep.deletedFiles());
        assertTrue(Files.isRegularFile(a));
        assertTrue(Files.isRegularFile(b));
    }
}
