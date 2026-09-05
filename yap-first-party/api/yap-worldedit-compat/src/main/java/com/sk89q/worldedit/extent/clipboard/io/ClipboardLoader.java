package com.sk89q.worldedit.extent.clipboard.io;

import com.sk89q.worldedit.extent.clipboard.Clipboard;

import java.io.File;
import java.io.IOException;

/**
 * Loads a schematic file into a {@link Clipboard}. YaPWorld registers the
 * real implementation on enable; without it, load fails closed (never empty).
 */
@FunctionalInterface
public interface ClipboardLoader {

    Clipboard load(ClipboardFormat format, File file) throws IOException;
}
