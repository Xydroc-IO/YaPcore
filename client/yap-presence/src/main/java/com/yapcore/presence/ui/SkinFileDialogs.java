package com.yapcore.presence.ui;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Async TinyFileDialogs open for PNG skin / cape files. */
public final class SkinFileDialogs {

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "yap-tailor-file-dialog");
        t.setDaemon(true);
        return t;
    });

    private SkinFileDialogs() {
    }

    public static CompletableFuture<Optional<Path>> openPng(String title) {
        return openPng(title, LocalSkinLibrary.preferredBrowseDir());
    }

    public static CompletableFuture<Optional<Path>> openPng(String title, Path defaultDir) {
        CompletableFuture<Optional<Path>> future = new CompletableFuture<>();
        EXEC.submit(() -> {
            String result = null;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(1);
                filters.put(stack.UTF8("*.png"));
                filters.flip();
                String defaultPath = defaultPathHint(defaultDir);
                result = TinyFileDialogs.tinyfd_openFileDialog(
                        title == null ? "Choose PNG" : title,
                        defaultPath,
                        filters,
                        "PNG images",
                        false);
            } catch (Exception ignored) {
            }
            future.complete(Optional.ofNullable(result).map(Paths::get));
        });
        return future;
    }

    private static String defaultPathHint(Path dir) {
        try {
            Path base = dir != null && Files.isDirectory(dir)
                    ? dir
                    : LocalSkinLibrary.preferredBrowseDir();
            // TinyFD expects a path ending in separator or a filename in the folder.
            String s = base.toAbsolutePath().toString();
            if (!s.endsWith("/") && !s.endsWith(java.io.File.separator)) {
                s = s + java.io.File.separator;
            }
            return s;
        } catch (Exception e) {
            return null;
        }
    }
}
