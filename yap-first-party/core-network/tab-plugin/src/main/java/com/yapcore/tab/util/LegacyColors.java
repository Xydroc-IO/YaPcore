package com.yapcore.tab.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class LegacyColors {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private LegacyColors() {
    }

    public static Component component(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        return LEGACY.deserialize(raw.replace('§', '&'));
    }

    public static String plain(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("&[0-9a-fk-or]", "").replaceAll("§[0-9a-fk-or]", "");
    }
}
