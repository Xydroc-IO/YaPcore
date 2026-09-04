package com.sk89q.worldedit.extent.clipboard;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Minimal clipboard holder for soft-deps (backed by block list). */
public final class Clipboard {

    public record Block(int x, int y, int z, BlockState state) {
    }

    private final List<Block> blocks = new ArrayList<>();
    private BlockVector3 origin = BlockVector3.at(0, 0, 0);
    private BlockVector3 dimensions = BlockVector3.at(1, 1, 1);

    public void setOrigin(BlockVector3 origin) {
        this.origin = origin == null ? BlockVector3.at(0, 0, 0) : origin;
    }

    public BlockVector3 getOrigin() {
        return origin;
    }

    public void setDimensions(BlockVector3 dimensions) {
        this.dimensions = dimensions == null ? BlockVector3.at(1, 1, 1) : dimensions;
    }

    public BlockVector3 getDimensions() {
        return dimensions;
    }

    public void setBlock(int x, int y, int z, BlockState state) {
        blocks.add(new Block(x, y, z, state));
    }

    public List<Block> getBlocks() {
        return Collections.unmodifiableList(blocks);
    }

    public int size() {
        return blocks.size();
    }
}
