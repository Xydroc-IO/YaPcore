package com.yapcore.moderation;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Optional audit hooks (Discord bridge, metrics). Fired from moderation plugin actions. */
public final class ModerationAudit {

    public interface Listener {
        void onAction(Action action);
    }

    public record Action(
            PunishmentType type,
            String actorName,
            String targetName,
            String reason,
            String detail
    ) {
    }

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private ModerationAudit() {
    }

    public static void register(Listener listener) {
        if (listener != null) {
            LISTENERS.add(listener);
        }
    }

    public static void unregister(Listener listener) {
        LISTENERS.remove(listener);
    }

    public static void fire(Action action) {
        for (Listener listener : LISTENERS) {
            try {
                listener.onAction(action);
            } catch (Exception ignored) {
            }
        }
    }
}
