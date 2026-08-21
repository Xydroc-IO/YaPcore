package com.yapcore.resourcepack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePackBundlerTest {

    @TempDir
    Path temp;

    @Test
    void singlePackPassthrough() throws Exception {
        writeZip(temp.resolve("a.zip"), "assets/x.txt", "A");
        assertEquals("a.zip", ResourcePackBundler.ensureOfferFile(temp, List.of("a.zip")));
    }

    @Test
    void multiPackMergesLaterWins() throws Exception {
        writeZip(temp.resolve("base.zip"), "assets/minecraft/textures/block/stone.png", "BASE");
        writeZip(temp.resolve("overlay.zip"), "assets/minecraft/textures/block/stone.png", "OVER");
        String offer = ResourcePackBundler.ensureOfferFile(temp, List.of("base.zip", "overlay.zip"));
        assertTrue(ResourcePackBundler.isBundleName(offer));
        Path bundle = temp.resolve(offer);
        assertTrue(Files.isRegularFile(bundle));
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(bundle))) {
            ZipEntry e;
            boolean found = false;
            while ((e = zis.getNextEntry()) != null) {
                if ("assets/minecraft/textures/block/stone.png".equals(e.getName())) {
                    assertEquals("OVER", new String(zis.readAllBytes(), StandardCharsets.UTF_8));
                    found = true;
                }
            }
            assertTrue(found);
        }
        // cached
        assertEquals(offer, ResourcePackBundler.ensureOfferFile(temp, List.of("base.zip", "overlay.zip")));
    }

    private static void writeZip(Path zip, String entry, String content) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("pack.mcmeta"));
            zos.write("{\"pack\":{\"pack_format\":34,\"description\":\"t\"}}".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry(entry));
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }
}
