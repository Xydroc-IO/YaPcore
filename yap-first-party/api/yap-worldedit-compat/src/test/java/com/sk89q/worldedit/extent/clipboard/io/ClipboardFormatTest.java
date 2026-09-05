package com.sk89q.worldedit.extent.clipboard.io;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClipboardFormatTest {

    @AfterEach
    void clearLoader() {
        ClipboardFormat.setLoader(null);
    }

    @Test
    void loadFailsClosedWithoutLoader(@TempDir Path dir) throws Exception {
        File file = dir.resolve("demo.schem").toFile();
        Files.writeString(file.toPath(), "not-empty");
        IOException ex = assertThrows(IOException.class,
                () -> ClipboardFormat.SPONGE_SCHEMATIC.load(file));
        assertTrue(ex.getMessage().contains("YaPWorld") || ex.getMessage().contains("ClipboardLoader"));
    }

    @Test
    void loadRejectsMissingFile() {
        assertThrows(IOException.class,
                () -> ClipboardFormat.SCHEMATIC.load(new File("/no/such/schematic.schem")));
    }

    @Test
    void loadDelegatesToRegisteredLoader(@TempDir Path dir) throws Exception {
        File file = dir.resolve("ok.yschem").toFile();
        Files.writeString(file.toPath(), "payload");
        AtomicBoolean called = new AtomicBoolean();
        ClipboardFormat.setLoader((format, f) -> {
            called.set(true);
            throw new IOException("loader-ok");
        });
        IOException ex = assertThrows(IOException.class, () -> ClipboardFormat.YSCHEM.load(file));
        assertTrue(called.get());
        assertTrue(ex.getMessage().contains("loader-ok"));
    }

    @Test
    void loadRejectsEmptyClipboardFromLoader(@TempDir Path dir) throws Exception {
        File file = dir.resolve("empty.schem").toFile();
        Files.writeString(file.toPath(), "x");
        ClipboardFormat.setLoader((format, f) -> new Clipboard());
        IOException ex = assertThrows(IOException.class,
                () -> ClipboardFormat.SPONGE_SCHEMATIC.load(file));
        assertTrue(ex.getMessage().toLowerCase().contains("empty"));
    }
}
