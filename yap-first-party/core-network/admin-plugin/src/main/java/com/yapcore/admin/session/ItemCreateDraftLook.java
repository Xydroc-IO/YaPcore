package com.yapcore.admin.session;

/** Item-look toggles for the custom-item create wizard (glow / unbreakable / rainbow). */
public final class ItemCreateDraftLook {

    boolean glow;
    boolean unbreakable;
    boolean rainbow;

    public boolean glow() {
        return glow;
    }

    public void setGlow(boolean glow) {
        this.glow = glow;
    }

    public void toggleGlow() {
        glow = !glow;
    }

    public boolean unbreakable() {
        return unbreakable;
    }

    public void setUnbreakable(boolean unbreakable) {
        this.unbreakable = unbreakable;
    }

    public void toggleUnbreakable() {
        unbreakable = !unbreakable;
    }

    public boolean rainbow() {
        return rainbow;
    }

    public void setRainbow(boolean rainbow) {
        this.rainbow = rainbow;
    }

    public void toggleRainbow() {
        rainbow = !rainbow;
    }
}
