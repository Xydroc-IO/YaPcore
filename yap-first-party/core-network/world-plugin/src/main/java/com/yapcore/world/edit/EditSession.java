package com.yapcore.world.edit;

import com.yapcore.world.util.BlockCodec;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One edit operation — block states before/after for undo/redo. */
public final class EditSession {

    public record BlockEdit(String world, int x, int y, int z, String before, String after) {
    }

    private final List<BlockEdit> edits = new ArrayList<>();

    public void record(String world, int x, int y, int z, String before, String after) {
        if (before != null && before.equals(after)) {
            return;
        }
        edits.add(new BlockEdit(world, x, y, z, before, after));
    }

    public List<BlockEdit> edits() {
        return Collections.unmodifiableList(edits);
    }

    public boolean isEmpty() {
        return edits.isEmpty();
    }

    public static String encode(World world, int x, int y, int z) {
        return BlockCodec.encode(world.getBlockAt(x, y, z));
    }

    public static Location location(BlockEdit edit) {
        World world = org.bukkit.Bukkit.getWorld(edit.world());
        return world == null ? null : new Location(world, edit.x(), edit.y(), edit.z());
    }
}
