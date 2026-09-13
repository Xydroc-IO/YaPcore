package com.yapcore.presence.ui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Discovers local PNG skins the player already has (Prism launcher skins, Downloads, etc.).
 */
public final class LocalSkinLibrary {

    public record Entry(Path path, String name, int width, int height) {
    }

    private LocalSkinLibrary() {
    }

    /** Preferred folders for the file dialog default path. */
    public static Path preferredBrowseDir() {
        Path prism = prismSkinsDir();
        if (prism != null && Files.isDirectory(prism)) {
            return prism;
        }
        Path downloads = Paths.get(System.getProperty("user.home"), "Downloads");
        if (Files.isDirectory(downloads)) {
            return downloads;
        }
        return Paths.get(System.getProperty("user.home"));
    }

    public static Path prismSkinsDir() {
        Path home = Paths.get(System.getProperty("user.home"));
        Path prism = home.resolve(".local/share/PrismLauncher/skins");
        if (Files.isDirectory(prism)) {
            return prism;
        }
        Path alt = home.resolve(".local/share/multimc/skins");
        return Files.isDirectory(alt) ? alt : null;
    }

    /**
     * Scan known folders for Minecraft-shaped PNGs (64×32, 64×64, 128×128, …).
     * Caps at {@code limit} newest-first.
     */
    public static List<Entry> scan(int limit) {
        Set<Path> roots = new LinkedHashSet<>();
        Path prism = prismSkinsDir();
        if (prism != null) {
            roots.add(prism);
        }
        Path downloads = Paths.get(System.getProperty("user.home"), "Downloads");
        if (Files.isDirectory(downloads)) {
            roots.add(downloads);
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.gameDirectory != null) {
            Path game = mc.gameDirectory.toPath();
            roots.add(game.resolve("skins"));
            roots.add(game);
        }
        List<Entry> found = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(root, "*.{png,PNG}")) {
                for (Path p : stream) {
                    if (!Files.isRegularFile(p)) {
                        continue;
                    }
                    Entry e = tryRead(p);
                    if (e != null) {
                        found.add(e);
                    }
                }
            } catch (IOException ignored) {
            }
        }
        found.sort(Comparator
                .comparingLong((Entry e) -> {
                    try {
                        return Files.getLastModifiedTime(e.path()).toMillis();
                    } catch (IOException ex) {
                        return 0L;
                    }
                })
                .reversed());
        if (found.size() > limit) {
            return List.copyOf(found.subList(0, limit));
        }
        return List.copyOf(found);
    }

    public static Entry tryRead(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return null;
        }
        try {
            long size = Files.size(path);
            if (size < 64 || size > 1_048_576) {
                return null;
            }
            try (InputStream in = Files.newInputStream(path); NativeImage img = NativeImage.read(in)) {
                if (img == null) {
                    return null;
                }
                int w = img.getWidth();
                int h = img.getHeight();
                if (!looksLikeSkin(w, h)) {
                    return null;
                }
                String name = path.getFileName().toString();
                int dot = name.lastIndexOf('.');
                if (dot > 0) {
                    name = name.substring(0, dot);
                }
                return new Entry(path, name, w, h);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** Classic / modern skin atlas sizes (64×32, 64×64, 128×128). */
    public static boolean looksLikeSkin(int w, int h) {
        if (w < 64 || h < 32 || w > 128 || h > 128) {
            return false;
        }
        if (w == h) {
            return w == 64 || w == 128;
        }
        return (w == 64 && h == 32) || (w == 128 && h == 64);
    }

    public static String displayLabel(Entry e) {
        if (e == null) {
            return "";
        }
        String folder = e.path().getParent() != null
                ? e.path().getParent().getFileName().toString()
                : "";
        String tag = folder.equalsIgnoreCase("skins") ? "launcher"
                : folder.equalsIgnoreCase("Downloads") ? "downloads"
                : folder.toLowerCase(Locale.ROOT);
        return e.name() + " · " + e.width() + "×" + e.height()
                + (tag.isBlank() ? "" : " · " + tag);
    }
}
