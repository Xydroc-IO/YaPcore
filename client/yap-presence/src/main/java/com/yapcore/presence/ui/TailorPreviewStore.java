package com.yapcore.presence.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;

import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Client-side Tailor preview state: selected wardrobe look, pending imports, and active slot.
 */
public final class TailorPreviewStore {

    private static final AtomicReference<Identifier> SKIN = new AtomicReference<>();
    private static final AtomicReference<Identifier> CAPE = new AtomicReference<>();
    private static final AtomicBoolean SLIM = new AtomicBoolean(false);
    private static final AtomicReference<String> STATUS = new AtomicReference<>("");
    /** Selected for preview; {@code -1} = current/active or import buffer. */
    private static final AtomicLong SELECTED_SLOT = new AtomicLong(-1L);
    private static final AtomicLong ACTIVE_SLOT = new AtomicLong(-1L);
    private static final CopyOnWriteArrayList<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean NOTIFY_SCHEDULED = new AtomicBoolean(false);

    private TailorPreviewStore() {
    }

    public static void setSkinTexture(Identifier id) {
        Identifier prev = SKIN.getAndSet(id);
        if (java.util.Objects.equals(prev, id)) {
            return;
        }
        notifyTexturesChanged();
    }

    public static void setCapeTexture(Identifier id) {
        Identifier prev = CAPE.getAndSet(id);
        if (java.util.Objects.equals(prev, id)) {
            return;
        }
        notifyTexturesChanged();
    }

    public static void setSlim(boolean slim) {
        if (SLIM.get() == slim) {
            return;
        }
        SLIM.set(slim);
        notifyTexturesChanged();
    }

    public static void setStatus(String status) {
        String next = status == null ? "" : status;
        String prev = STATUS.getAndSet(next);
        if (java.util.Objects.equals(prev, next)) {
            return;
        }
        notifyTexturesChanged();
    }

    public static void setSelectedSlotId(long slotId) {
        if (SELECTED_SLOT.get() == slotId) {
            return;
        }
        SELECTED_SLOT.set(slotId);
        notifyTexturesChanged();
    }

    public static void setActiveSlotId(long slotId) {
        if (ACTIVE_SLOT.get() == slotId) {
            return;
        }
        ACTIVE_SLOT.set(slotId);
        notifyTexturesChanged();
    }

    public static long selectedSlotId() {
        return SELECTED_SLOT.get();
    }

    public static long activeSlotId() {
        return ACTIVE_SLOT.get();
    }

    public static Identifier skinTexture() {
        return SKIN.get();
    }

    public static Identifier capeTexture() {
        return CAPE.get();
    }

    public static boolean slim() {
        return SLIM.get();
    }

    public static PlayerModelType modelType() {
        return slim() ? PlayerModelType.SLIM : PlayerModelType.WIDE;
    }

    public static String status() {
        return STATUS.get();
    }

    public static Identifier previewSkinId(UUID player) {
        return Identifier.fromNamespaceAndPath(
                "yap-presence",
                "preview/skin/" + (player == null ? "local" : player.toString()));
    }

    public static Identifier previewCapeId(UUID player) {
        return Identifier.fromNamespaceAndPath(
                "yap-presence",
                "preview/cape/" + (player == null ? "local" : player.toString()));
    }

    public static void clear() {
        SKIN.set(null);
        CAPE.set(null);
        STATUS.set("");
        SELECTED_SLOT.set(-1L);
    }

    public static void addListener(Runnable r) {
        if (r != null) {
            LISTENERS.add(r);
        }
    }

    public static void removeListener(Runnable r) {
        LISTENERS.remove(r);
    }

    public static void notifyTexturesChanged() {
        // Coalesce storms from async local/wardrobe PNG loads — full wardrobe rebuild
        // on every texture is what caused Missing resource spam + exit 9 thrash.
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            flushListeners();
            return;
        }
        if (!NOTIFY_SCHEDULED.compareAndSet(false, true)) {
            return;
        }
        mc.execute(() -> {
            NOTIFY_SCHEDULED.set(false);
            flushListeners();
        });
    }

    private static void flushListeners() {
        for (Runnable r : LISTENERS) {
            try {
                r.run();
            } catch (Exception ignored) {
            }
        }
    }

    /** Apply wardrobe sync: slim flags + active slot badge; prefetch handled by caller. */
    public static void applyWardrobeSync(PresenceUiMessages.WardrobeView view) {
        if (view == null) {
            return;
        }
        setSlim(view.slim());
        setActiveSlotId(view.activeSlotId());
        long selected = selectedSlotId();
        if (selected > 0) {
            boolean stillThere = false;
            for (PresenceUiMessages.SlotView s : view.slots()) {
                if (s.id() == selected) {
                    stillThere = true;
                    break;
                }
            }
            if (!stillThere) {
                setSelectedSlotId(view.activeSlotId() > 0 ? view.activeSlotId() : -1L);
            }
        } else if (view.activeSlotId() > 0 && selected < 0 && skinTexture() == null) {
            setSelectedSlotId(view.activeSlotId());
        }
    }
}
