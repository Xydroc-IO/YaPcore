package com.yapcore.presence.ui;

import com.yapcore.presence.emote.EmoteClipLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Holds the last wardrobe / emote catalog pushed over {@code yap:presence}. */
public final class PresenceUiStore {

    private static volatile PresenceUiMessages.WardrobeView wardrobe =
            new PresenceUiMessages.WardrobeView(false, "", "", List.of());
    private static volatile List<PresenceUiMessages.EmoteView> emotes = List.of();
    private static volatile String lastStatus = "";
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private PresenceUiStore() {
    }

    public static PresenceUiMessages.WardrobeView wardrobe() {
        return wardrobe;
    }

    /**
     * Server catalog when available; otherwise bundled {@code yap.emote/1} clips so the
     * picker is never empty before HELLO/SYNC lands.
     */
    public static List<PresenceUiMessages.EmoteView> emotes() {
        if (!emotes.isEmpty()) {
            return emotes;
        }
        return bundledEmotes();
    }

    public static List<PresenceUiMessages.EmoteView> bundledEmotes() {
        List<PresenceUiMessages.EmoteView> out = new ArrayList<>();
        for (EmoteClipLoader.Clip clip : EmoteClipLoader.all()) {
            out.add(new PresenceUiMessages.EmoteView(clip.bedrockEmoteId(), clip.name()));
        }
        return List.copyOf(out);
    }

    public static String lastStatus() {
        return lastStatus;
    }

    public static void setWardrobe(PresenceUiMessages.WardrobeView view) {
        if (view != null) {
            wardrobe = view;
            TailorPreviewStore.applyWardrobeSync(view);
            for (PresenceUiMessages.SlotView slot : view.slots()) {
                com.yapcore.presence.PresenceTextureCache.ensureWardrobeSlot(
                        slot.id(), slot.skinUrl(), slot.capeUrl());
            }
            if (!view.activeUrl().isBlank()) {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc != null && mc.player != null) {
                    com.yapcore.presence.PresenceTextureCache.ensureDownloaded(
                            mc.player.getUUID(), view.activeUrl());
                }
            }
            notifyListeners();
        }
    }

    public static void setEmotes(List<PresenceUiMessages.EmoteView> list) {
        emotes = list == null ? List.of() : List.copyOf(list);
        notifyListeners();
    }

    public static void setStatus(String status) {
        lastStatus = status == null ? "" : status;
        notifyListeners();
    }

    public static void addListener(Runnable r) {
        if (r != null) {
            LISTENERS.add(r);
        }
    }

    public static void removeListener(Runnable r) {
        LISTENERS.remove(r);
    }

    private static void notifyListeners() {
        for (Runnable r : LISTENERS) {
            try {
                r.run();
            } catch (Exception ignored) {
            }
        }
    }
}
