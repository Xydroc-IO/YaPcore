package org.bukkit;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Namespaced resource key (Paper/Bukkit compatible). */
public final class NamespacedKey {

    private final String namespace;
    private final String key;

    public NamespacedKey(String namespace, String key) {
        this.namespace = Objects.requireNonNull(namespace).toLowerCase(Locale.ROOT);
        this.key = Objects.requireNonNull(key).toLowerCase(Locale.ROOT);
    }

    public static NamespacedKey minecraft(String key) {
        return new NamespacedKey("minecraft", key);
    }

    public static NamespacedKey fromString(String s) {
        int i = s.indexOf(':');
        if (i < 0) {
            return minecraft(s);
        }
        return new NamespacedKey(s.substring(0, i), s.substring(i + 1));
    }

    public String getNamespace() {
        return namespace;
    }

    public String getKey() {
        return key;
    }

    @Override
    public String toString() {
        return namespace + ':' + key;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NamespacedKey other)) {
            return false;
        }
        return namespace.equals(other.namespace) && key.equals(other.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, key);
    }

    /** Stable UUID for offline players from name. */
    public static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes());
    }
}
