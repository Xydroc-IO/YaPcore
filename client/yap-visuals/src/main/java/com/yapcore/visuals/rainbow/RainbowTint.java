package com.yapcore.visuals.rainbow;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ARGB;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Cycling mesh tint + per-frame tagged quad lists for rainbow items. */
public final class RainbowTint {

    private static final int[] COLORS = {
            argb(255, 72, 72),
            argb(255, 168, 48),
            argb(255, 224, 64),
            argb(72, 224, 112),
            argb(72, 168, 255),
            argb(168, 96, 255),
            argb(232, 72, 208)
    };

    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<Object> CURRENT_QUADS = new ThreadLocal<>();
    private static final Set<Object> TAGGED_QUADS = Collections.newSetFromMap(new IdentityHashMap<>());

    private RainbowTint() {
    }

    public static void setActive(boolean active) {
        ACTIVE.set(active);
    }

    public static void clear() {
        ACTIVE.set(Boolean.FALSE);
    }

    public static boolean active() {
        return Boolean.TRUE.equals(ACTIVE.get());
    }

    public static void tagQuads(Object quads) {
        if (quads != null && active()) {
            TAGGED_QUADS.add(quads);
        }
    }

    public static boolean isTagged(Object quads) {
        return quads != null && TAGGED_QUADS.contains(quads);
    }

    public static void beginSubmit(Object quads) {
        CURRENT_QUADS.set(quads);
    }

    public static void endSubmit() {
        CURRENT_QUADS.remove();
    }

    public static boolean isCurrentSubmitTagged() {
        return isTagged(CURRENT_QUADS.get());
    }

    public static void clearTags() {
        TAGGED_QUADS.clear();
        CURRENT_QUADS.remove();
    }

    /** Soft hue wash over the existing item tint (keeps texture detail). */
    public static int applyToTint(int original) {
        int soft = lerpArgb(0xFFFFFFFF, currentArgb(), 0.55f);
        int base = original == 0 ? -1 : original;
        return ARGB.multiply(base, soft);
    }

    public static int currentArgb() {
        long ticks;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            ticks = mc.level.getGameTime();
        } else {
            ticks = System.currentTimeMillis() / 50L;
        }
        float phase = (ticks % (COLORS.length * 4L)) / 4.0f;
        int i = (int) phase;
        float t = phase - i;
        return lerpArgb(
                COLORS[Math.floorMod(i, COLORS.length)],
                COLORS[Math.floorMod(i + 1, COLORS.length)],
                t);
    }

    private static int lerpArgb(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bg - ag) * t);
        int bl = Math.round(ab + (bb - ab) * t);
        return argb(r, g, bl);
    }

    private static int argb(int r, int g, int b) {
        return 0xFF000000 | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }
}
