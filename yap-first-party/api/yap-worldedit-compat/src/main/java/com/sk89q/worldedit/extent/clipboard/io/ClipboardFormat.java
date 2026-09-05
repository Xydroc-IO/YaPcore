package com.sk89q.worldedit.extent.clipboard.io;

import com.sk89q.worldedit.extent.clipboard.Clipboard;

import java.io.File;
import java.io.IOException;

/**
 * Minimal schematic format facade. Soft-deps can detect the class;
 * YaPWorld registers a real {@link ClipboardLoader} on enable.
 */
public enum ClipboardFormat {
    SCHEMATIC("schematic", "schematic"),
    SPONGE_SCHEMATIC("sponge.schematic", "schem"),
    YSCHEM("yap.schematic", "yschem");

    private static volatile ClipboardLoader loader;

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

    /** YaPWorld binds a real loader; clear on disable. */
    public static void setLoader(ClipboardLoader next) {
        loader = next;
    }

    public static ClipboardLoader getLoader() {
        return loader;
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

    /**
     * Load a schematic into a clipboard. Fail closed — never returns an empty
     * clipboard for a real file (that caused silent air pastes for soft-deps).
     */
    public Clipboard load(File file) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IOException("schematic not found: " + (file == null ? "null" : file.getAbsolutePath()));
        }
        ClipboardLoader bound = loader;
        if (bound == null) {
            throw new IOException(
                    "WorldEdit shim: schematic load requires YaPWorld (ClipboardLoader not registered). "
                            + "Use /schem via YaPWorld or ensure YaPWorld enabled before WorldEdit soft-deps.");
        }
        Clipboard clip = bound.load(this, file);
        if (clip == null) {
            throw new IOException("schematic load returned null: " + file.getName());
        }
        if (clip.size() == 0) {
            throw new IOException("schematic is empty or unsupported: " + file.getName());
        }
        return clip;
    }
}
