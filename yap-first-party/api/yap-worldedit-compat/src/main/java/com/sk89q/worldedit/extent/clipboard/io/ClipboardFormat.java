package com.sk89q.worldedit.extent.clipboard.io;

import com.sk89q.worldedit.extent.clipboard.Clipboard;

import java.io.File;
import java.io.IOException;

/**
 * Minimal schematic format facade. Soft-deps can detect the class;
 * YaPWorld performs real I/O via its own SchematicIO / Sponge importer.
 */
public enum ClipboardFormat {
    SCHEMATIC("schematic", "schematic"),
    SPONGE_SCHEMATIC("sponge.schematic", "schem"),
    YSCHEM("yap.schematic", "yschem");

    private final String name;
    private final String extension;

    ClipboardFormat(String name, String extension) {
        this.name = name;
        this.extension = extension;
    }

    public String getName() {
        return name;
    }

    public String getPrimaryFileExtension() {
        return extension;
    }

    public static ClipboardFormat findByFile(File file) {
        if (file == null) {
            return null;
        }
        String n = file.getName().toLowerCase();
        if (n.endsWith(".schem")) {
            return SPONGE_SCHEMATIC;
        }
        if (n.endsWith(".schematic")) {
            return SCHEMATIC;
        }
        if (n.endsWith(".yschem")) {
            return YSCHEM;
        }
        if (n.endsWith(".litematic")) {
            return SPONGE_SCHEMATIC;
        }
        return null;
    }

    /** No-op load — soft-deps should use YaPWorld services for real schematics. */
    public Clipboard load(File file) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IOException("schematic not found");
        }
        return new Clipboard();
    }
}
