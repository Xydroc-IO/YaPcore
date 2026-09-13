package com.yapcore.presence.ui;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/** Client-side decode of presence UI payloads (mirrors Tailor {@code PresenceUiCodec}). */
public final class PresenceUiMessages {

    private PresenceUiMessages() {
    }

    public record SlotView(long id, String name, boolean slim, String skinUrl, String capeUrl) {
    }

    public record WardrobeView(
            boolean slim,
            String activeUrl,
            String activeCape,
            long activeSlotId,
            List<SlotView> slots) {
        public WardrobeView(boolean slim, String activeUrl, String activeCape, List<SlotView> slots) {
            this(slim, activeUrl, activeCape, -1L, slots);
        }
    }

    public record EmoteView(String id, String name) {
    }

    public static Optional<WardrobeView> decodeWardrobe(String text) {
        if (text == null) {
            return Optional.empty();
        }
        if (text.regionMatches(true, 0, "WARDROBE|v2|", 0, 12)) {
            return decodeV2(text);
        }
        if (text.regionMatches(true, 0, "WARDROBE|v1|", 0, 12)) {
            return decodeV1(text);
        }
        return Optional.empty();
    }

    private static Optional<WardrobeView> decodeV2(String text) {
        String[] p = text.split("\\|", -1);
        if (p.length < 6) {
            return Optional.empty();
        }
        boolean slim = "1".equals(p[2]);
        String activeUrl = unb64(p[3]);
        String activeCape = unb64(p[4]);
        long activeSlotId = -1L;
        try {
            activeSlotId = Long.parseLong(p[5].trim());
        } catch (NumberFormatException ignored) {
        }
        return Optional.of(new WardrobeView(
                slim, activeUrl, activeCape, activeSlotId, parseSlots(p.length >= 7 ? p[6] : "")));
    }

    private static Optional<WardrobeView> decodeV1(String text) {
        String[] p = text.split("\\|", -1);
        if (p.length < 5) {
            return Optional.empty();
        }
        boolean slim = "1".equals(p[2]);
        String activeUrl = unb64(p[3]);
        String activeCape = unb64(p[4]);
        return Optional.of(new WardrobeView(
                slim, activeUrl, activeCape, -1L, parseSlots(p.length >= 6 ? p[5] : "")));
    }

    private static List<SlotView> parseSlots(String body) {
        List<SlotView> slots = new ArrayList<>();
        if (body == null || body.isBlank()) {
            return List.copyOf(slots);
        }
        for (String part : body.split(";", -1)) {
            if (part.isBlank()) {
                continue;
            }
            String[] f = part.split(",", -1);
            if (f.length < 5) {
                continue;
            }
            try {
                slots.add(new SlotView(
                        Long.parseLong(f[0]),
                        unb64(f[2]),
                        "1".equals(f[1]),
                        unb64(f[3]),
                        unb64(f[4])));
            } catch (NumberFormatException ignored) {
            }
        }
        return List.copyOf(slots);
    }

    public static Optional<List<EmoteView>> decodeEmoteCatalog(String text) {
        if (text == null || !text.regionMatches(true, 0, "EMOTE_CATALOG|v1|", 0, 16)) {
            return Optional.empty();
        }
        String body = text.substring(16);
        List<EmoteView> out = new ArrayList<>();
        if (!body.isBlank()) {
            for (String part : body.split(";", -1)) {
                if (part.isBlank()) {
                    continue;
                }
                int comma = part.indexOf(',');
                if (comma <= 0) {
                    continue;
                }
                out.add(new EmoteView(part.substring(0, comma), unb64(part.substring(comma + 1))));
            }
        }
        return Optional.of(List.copyOf(out));
    }

    public static String b64(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static String unb64(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return "";
        }
        try {
            return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}
