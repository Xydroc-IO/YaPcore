package com.yapcore.bedrockblocks;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

/** PDC keys — namespace {@code yapbedrock}, key {@code port_id}. */
public final class PortBlockKeys {

    public static final String NAMESPACE = "yapbedrock";
    public static final String PORT_ID_KEY = "port_id";
    public static final PersistentDataType<String, String> STRING = PersistentDataType.STRING;

    private final NamespacedKey portId;

    public PortBlockKeys() {
        // Explicit namespace (not plugin-scoped) so chassis / packs share identity.
        this.portId = new NamespacedKey(NAMESPACE, PORT_ID_KEY);
    }

    public NamespacedKey portId() {
        return portId;
    }
}
