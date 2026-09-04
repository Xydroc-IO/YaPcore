package com.sk89q.worldedit;

import com.sk89q.worldedit.extent.clipboard.ClipboardHolder;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;

/** Per-player WorldEdit session (selection + clipboard slot). */
public final class LocalSession {

    private BlockVector3 pos1;
    private BlockVector3 pos2;
    private Region selection;
    private int clipboardSlot;
    private ClipboardHolder clipboardHolder;

    public void setPos1(BlockVector3 pos) {
        this.pos1 = pos;
        refreshCuboid();
    }

    public void setPos2(BlockVector3 pos) {
        this.pos2 = pos;
        refreshCuboid();
    }

    public BlockVector3 getPos1() {
        return pos1;
    }

    public BlockVector3 getPos2() {
        return pos2;
    }

    public Region getSelection() {
        return selection;
    }

    public void setSelection(Region region) {
        this.selection = region;
    }

    public int getClipboardSlot() {
        return clipboardSlot;
    }

    public void setClipboardSlot(int slot) {
        this.clipboardSlot = Math.max(0, slot);
    }

    public ClipboardHolder getClipboard() {
        return clipboardHolder;
    }

    public void setClipboard(ClipboardHolder holder) {
        this.clipboardHolder = holder;
    }

    private void refreshCuboid() {
        if (pos1 != null && pos2 != null) {
            selection = new CuboidRegion(pos1, pos2);
        }
    }
}
