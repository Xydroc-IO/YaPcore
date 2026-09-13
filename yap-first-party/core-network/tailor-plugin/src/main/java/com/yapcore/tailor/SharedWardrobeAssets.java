package com.yapcore.tailor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Shared chassis {@code skins/wardrobe/{uuid}/{slotId}.png} layout used by Tailor and
 * served by {@code ResourcePackHttpServer} at {@code /skin/wardrobe/...}.
 * Failures throw — no silent skip.
 */
public final class SharedWardrobeAssets {

    private SharedWardrobeAssets() {
    }

    public static Path skinPath(Path skinsRoot, UUID playerUuid, long slotId) {
        return skinsRoot.resolve("wardrobe").resolve(playerUuid.toString()).resolve(slotId + ".png");
    }

    public static Path capePath(Path skinsRoot, UUID playerUuid, long slotId) {
        return skinsRoot.resolve("wardrobe").resolve(playerUuid.toString()).resolve(slotId + "_cape.png");
    }

    public static void writeSkin(Path skinsRoot, UUID playerUuid, long slotId, byte[] png) throws IOException {
        if (skinsRoot == null || playerUuid == null || slotId <= 0) {
            throw new IOException("Invalid wardrobe skin write args");
        }
        if (png == null || png.length == 0) {
            throw new IOException("Empty wardrobe skin PNG");
        }
        Path path = skinPath(skinsRoot, playerUuid, slotId);
        Files.createDirectories(path.getParent());
        Files.write(path, png);
    }

    public static void writeCape(Path skinsRoot, UUID playerUuid, long slotId, byte[] png) throws IOException {
        if (skinsRoot == null || playerUuid == null || slotId <= 0) {
            throw new IOException("Invalid wardrobe cape write args");
        }
        Path path = capePath(skinsRoot, playerUuid, slotId);
        if (png == null || png.length == 0) {
            Files.deleteIfExists(path);
            return;
        }
        Files.createDirectories(path.getParent());
        Files.write(path, png);
    }

    public static byte[] readSkin(Path skinsRoot, UUID playerUuid, long slotId) throws IOException {
        Path path = skinPath(skinsRoot, playerUuid, slotId);
        if (!Files.isRegularFile(path)) {
            return null;
        }
        return Files.readAllBytes(path);
    }

    public static void delete(Path skinsRoot, UUID playerUuid, long slotId) throws IOException {
        if (skinsRoot == null || playerUuid == null || slotId <= 0) {
            throw new IOException("Invalid wardrobe delete args");
        }
        Files.deleteIfExists(skinPath(skinsRoot, playerUuid, slotId));
        Files.deleteIfExists(capePath(skinsRoot, playerUuid, slotId));
        Path dir = skinsRoot.resolve("wardrobe").resolve(playerUuid.toString());
        if (Files.isDirectory(dir)) {
            try (var stream = Files.list(dir)) {
                if (stream.findAny().isEmpty()) {
                    Files.deleteIfExists(dir);
                }
            }
        }
    }
}
