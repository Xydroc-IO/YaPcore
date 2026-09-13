package com.yapcore.tailor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Real I/O lifecycle for shared chassis wardrobe PNGs: write → read → overwrite → delete.
 * Also asserts hard-fail when skins root cannot be resolved.
 */
final class SharedWardrobeAssetsLifecycleTest {

    @TempDir
    Path tempHome;

    @Test
    void saveApplyDeleteFileLifecycle() throws Exception {
        Path skins = tempHome.resolve("skins");
        Files.createDirectories(skins);
        UUID uuid = UUID.fromString("22222222-2222-2222-2222-222222222222");
        long slotId = 42L;
        byte[] skinPng = "PNG-SKIN-BYTES".getBytes(StandardCharsets.UTF_8);
        byte[] capePng = "PNG-CAPE-BYTES".getBytes(StandardCharsets.UTF_8);

        SharedWardrobeAssets.writeSkin(skins, uuid, slotId, skinPng);
        Path skinPath = SharedWardrobeAssets.skinPath(skins, uuid, slotId);
        assertTrue(Files.isRegularFile(skinPath));
        assertArrayEquals(skinPng, SharedWardrobeAssets.readSkin(skins, uuid, slotId));

        SharedWardrobeAssets.writeCape(skins, uuid, slotId, capePng);
        assertTrue(Files.isRegularFile(SharedWardrobeAssets.capePath(skins, uuid, slotId)));

        // Overwrite skin (apply-style refresh)
        byte[] refreshed = "PNG-SKIN-REFRESHED".getBytes(StandardCharsets.UTF_8);
        SharedWardrobeAssets.writeSkin(skins, uuid, slotId, refreshed);
        assertArrayEquals(refreshed, Files.readAllBytes(skinPath));

        SharedWardrobeAssets.delete(skins, uuid, slotId);
        assertFalse(Files.exists(skinPath));
        assertFalse(Files.exists(SharedWardrobeAssets.capePath(skins, uuid, slotId)));
        assertNull(SharedWardrobeAssets.readSkin(skins, uuid, slotId));
        Path slotDir = skins.resolve("wardrobe").resolve(uuid.toString());
        assertFalse(Files.exists(slotDir));
    }

    @Test
    void writeSkinHardFailsOnEmptyOrNullRoot() {
        UUID uuid = UUID.randomUUID();
        assertThrows(IOException.class, () -> SharedWardrobeAssets.writeSkin(null, uuid, 1L, new byte[] {1}));
        assertThrows(IOException.class,
                () -> SharedWardrobeAssets.writeSkin(tempHome, uuid, 1L, new byte[0]));
    }

    @Test
    void chassisResolveHardFailsWithoutHome() {
        String prev = System.getProperty("yapcore.home");
        System.clearProperty("yapcore.home");
        try {
            assertNull(ChassisSkinPush.resolveSharedSkinsDirOrNull(null));
            IOException ex = assertThrows(
                    IOException.class, () -> ChassisSkinPush.requireSharedSkinsDir(null));
            assertNotNull(ex.getMessage());
            assertTrue(ex.getMessage().contains("Cannot resolve shared skins"));
        } finally {
            if (prev != null) {
                System.setProperty("yapcore.home", prev);
            } else {
                System.clearProperty("yapcore.home");
            }
        }
    }

    @Test
    void chassisResolveUsesYapcoreHome() throws Exception {
        String prev = System.getProperty("yapcore.home");
        System.setProperty("yapcore.home", tempHome.toAbsolutePath().toString());
        try {
            Path skins = ChassisSkinPush.resolveSharedSkinsDirOrNull(null);
            assertNotNull(skins);
            assertEqualsNormalized(tempHome.resolve("skins"), skins);

            UUID uuid = UUID.randomUUID();
            byte[] png = "HOME-MIRROR".getBytes(StandardCharsets.UTF_8);
            SharedWardrobeAssets.writeSkin(skins, uuid, 9L, png);
            Path expected = skins.resolve("wardrobe").resolve(uuid.toString()).resolve("9.png");
            assertTrue(Files.isRegularFile(expected));
            assertArrayEquals(png, Files.readAllBytes(expected));

            SharedWardrobeAssets.writeCape(skins, uuid, 9L, "CAPE".getBytes(StandardCharsets.UTF_8));
            SharedWardrobeAssets.delete(skins, uuid, 9L);
            assertFalse(Files.exists(expected));
        } finally {
            if (prev != null) {
                System.setProperty("yapcore.home", prev);
            } else {
                System.clearProperty("yapcore.home");
            }
        }
    }

    private static void assertEqualsNormalized(Path expected, Path actual) {
        assertTrue(expected.toAbsolutePath().normalize().equals(actual.toAbsolutePath().normalize()),
                () -> "expected " + expected + " but was " + actual);
    }
}
