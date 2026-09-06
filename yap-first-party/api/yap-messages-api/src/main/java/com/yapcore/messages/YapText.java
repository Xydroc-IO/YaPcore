package com.yapcore.messages;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Locale;
import java.util.Map;

/**
 * Shared legacy ({@code &} / {@code §} / hex) → Adventure text for all YaP plugins.
 */
public final class YapText {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    private YapText() {
    }

    public static Component component(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        return LEGACY.deserialize(raw.replace('§', '&'));
    }

    public static Component component(String template, Map<String, String> placeholders) {
        return component(apply(template, placeholders));
    }

    /** Replaces both {@code {key}} and {@code %key%} (case-insensitive keys). */
    public static String apply(String template, Map<String, String> placeholders) {
        if (template == null) {
            return "";
        }
        if (placeholders == null || placeholders.isEmpty()) {
            return template;
        }
        String out = template;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            String value = e.getValue() == null ? "" : e.getValue();
            String key = e.getKey();
            out = out.replace("{" + key + "}", value)
                    .replace("%" + key + "%", value);
            String lower = key.toLowerCase(Locale.ROOT);
            if (!lower.equals(key)) {
                out = out.replace("{" + lower + "}", value)
                        .replace("%" + lower + "%", value);
            }
        }
        return out;
    }

    public static String apply(String template, String... keyValues) {
        return apply(template, toMap(keyValues));
    }

    public static Component component(String template, String... keyValues) {
        return component(apply(template, keyValues));
    }

    public static String plain(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("(?i)[&§][0-9a-fk-orx]", "")
                .replaceAll("(?i)[&§]#[0-9a-f]{6}", "");
    }

    public static Map<String, String> toMap(String... keyValues) {
        if (keyValues == null || keyValues.length == 0) {
            return Map.of();
        }
        if ((keyValues.length & 1) != 0) {
            throw new IllegalArgumentException("keyValues must be even-length");
        }
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1] == null ? "" : keyValues[i + 1]);
        }
        return Map.copyOf(map);
    }
}
