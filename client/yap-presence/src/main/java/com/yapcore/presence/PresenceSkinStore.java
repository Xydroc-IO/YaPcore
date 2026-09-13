package com.yapcore.presence;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client-side registry of presence skins keyed by player UUID. */
public final class PresenceSkinStore {

    private static final ConcurrentHashMap<UUID, PresenceSkin> BY_UUID = new ConcurrentHashMap<>();

    private PresenceSkinStore() {
    }

    public static void put(PresenceSkin skin) {
        if (skin == null) {
            return;
        }
        BY_UUID.put(skin.playerUuid(), skin);
    }

    public static Optional<PresenceSkin> get(UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_UUID.get(uuid));
    }

    public static boolean has(UUID uuid) {
        return uuid != null && BY_UUID.containsKey(uuid);
    }

    public static void remove(UUID uuid) {
        if (uuid != null) {
            BY_UUID.remove(uuid);
        }
    }

    public static void clear() {
        BY_UUID.clear();
    }

    public static Collection<PresenceSkin> all() {
        return BY_UUID.values();
    }
}
