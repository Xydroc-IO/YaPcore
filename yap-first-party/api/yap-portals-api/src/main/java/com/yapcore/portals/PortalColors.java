package com.yapcore.portals;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Named portal colors (vanilla dye names) — RGB for particle dust.
 * Kept Bukkit-free so the API module stays unit-testable.
 */
public final class PortalColors {

    public static final String DEFAULT = "purple";

    /** Dye name → packed RGB (0xRRGGBB). */
    private static final Map<String, Integer> RGB = Map.ofEntries(
            Map.entry("white", 0xF9FFFE),
            Map.entry("orange", 0xF9801D),
            Map.entry("magenta", 0xC74EBD),
            Map.entry("light_blue", 0x3AB3DA),
            Map.entry("yellow", 0xFED83D),
            Map.entry("lime", 0x80C71F),
            Map.entry("pink", 0xF38BAA),
            Map.entry("gray", 0x474F52),
            Map.entry("light_gray", 0x9D9D97),
            Map.entry("cyan", 0x169C9C),
            Map.entry("purple", 0x8932B8),
            Map.entry("blue", 0x3C44AA),
            Map.entry("brown", 0x835432),
            Map.entry("green", 0x5E7C16),
            Map.entry("red", 0xB02E26),
            Map.entry("black", 0x1D1D21)
    );

    private PortalColors() {
    }

    public static String normalize(String raw) {
        return parseKey(raw).orElse(DEFAULT);
    }

    public static Optional<String> parse(String raw) {
        return parseKey(raw);
    }

    public static int rgb(String raw) {
        return RGB.getOrDefault(normalize(raw), RGB.get(DEFAULT));
    }

    public static int red(String raw) {
        return (rgb(raw) >> 16) & 0xFF;
    }

    public static int green(String raw) {
        return (rgb(raw) >> 8) & 0xFF;
    }

    public static int blue(String raw) {
        return rgb(raw) & 0xFF;
    }

    public static java.util.List<String> names() {
        return RGB.keySet().stream().sorted().collect(Collectors.toList());
    }

    private static Optional<String> parseKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String key = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if ("grey".equals(key)) {
            key = "gray";
        } else if ("light_grey".equals(key)) {
            key = "light_gray";
        }
        return RGB.containsKey(key) ? Optional.of(key) : Optional.empty();
    }
}
