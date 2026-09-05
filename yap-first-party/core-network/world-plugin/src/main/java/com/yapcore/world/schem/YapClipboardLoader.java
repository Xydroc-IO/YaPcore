package com.yapcore.world.schem;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardLoader;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Binds WorldEdit {@link ClipboardFormat#load} to YaPWorld schematic I/O.
 */
public final class YapClipboardLoader implements ClipboardLoader {

    @Override
    public Clipboard load(ClipboardFormat format, File file) throws IOException {
        Path path = file.toPath();
        Schematic schematic = SchematicCatalog.load(path);
        Clipboard clip = new Clipboard();
        Schematic.Bounds bounds = schematic.bounds();
        clip.setOrigin(BlockVector3.at(0, 0, 0));
        clip.setDimensions(BlockVector3.at(
                Math.max(1, bounds.sizeX()),
                Math.max(1, bounds.sizeY()),
                Math.max(1, bounds.sizeZ())));
        for (Schematic.BlockEntry entry : schematic.blocks()) {
            String encoded = entry.encoded();
            if (encoded == null || encoded.isBlank()) {
                continue;
            }
            BlockState state = BlockState.get(encoded);
            clip.setBlock(entry.dx(), entry.dy(), entry.dz(), state);
        }
        if (clip.size() == 0) {
            throw new IOException("no blocks in schematic: " + file.getName());
        }
        return clip;
    }
}
