package com.sk89q.worldedit.extent.clipboard;

/** Session clipboard wrapper. */
public final class ClipboardHolder {

    private Clipboard clipboard;

    public ClipboardHolder() {
    }

    public ClipboardHolder(Clipboard clipboard) {
        this.clipboard = clipboard;
    }

    public Clipboard getClipboard() {
        return clipboard;
    }

    public void setClipboard(Clipboard clipboard) {
        this.clipboard = clipboard;
    }

    public boolean isEmpty() {
        return clipboard == null || clipboard.size() == 0;
    }
}
