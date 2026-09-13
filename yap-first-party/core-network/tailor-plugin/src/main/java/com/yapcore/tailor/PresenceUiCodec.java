package com.yapcore.tailor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Pipe-safe codec for {@code yap:presence} wardrobe / emote-catalog UI payloads.
 * URLs and names are Base64 so {@code |} cannot break framing.
 *
 * <pre>
 * WARDROBE|v2|&lt;slim&gt;|&lt;b64 activeUrl&gt;|&lt;b64 activeCape&gt;|&lt;activeSlotId|-1&gt;|&lt;slots&gt;
 * slot = &lt;id&gt;,&lt;slim&gt;,&lt;b64 name&gt;,&lt;b64 skinUrl&gt;,&lt;b64 capeUrl&gt;
 * </pre>
 */
public final class PresenceUiCodec {

    private PresenceUiCodec() {
    }

    public record SlotView(long id, String name, boolean slim, String skinUrl, String capeUrl) {
    }

    public record WardrobeView(
            boolean slim,
            String activeUrl,
            String activeCape,
            long activeSlotId,
            List<SlotView> slots) {
        /** Convenience when no active slot is worn ({@code -1}). */
        public WardrobeView(boolean slim, String activeUrl, String activeCape, List<SlotView> slots) {
            this(slim, activeUrl, activeCape, -1L, slots);
        }
    }

    public record EmoteView(String id, String name) {
    }

    public static String encodeWardrobe(WardrobeView view) {
        StringBuilder sb = new StringBuilder("WARDROBE|v2|");
        sb.append(view.slim() ? '1' : '0').append('|');
        sb.append(b64(view.activeUrl())).append('|');
        sb.append(b64(view.activeCape())).append('|');
        long activeSlot = view.activeSlotId() > 0 ? view.activeSlotId() : -1L;
        sb.append(activeSlot).append('|');
        List<SlotView> slots = view.slots() == null ? List.of() : view.slots();
        List<String> parts = new ArrayList<>(slots.size());
        for (SlotView s : slots) {
            parts.add(s.id()
                    + ","
                    + (s.slim() ? '1' : '0')
                    + ","
                    + b64(s.name())
                    + ","
                    + b64(s.skinUrl())
                    + ","
                    + b64(s.capeUrl()));
        }
        sb.append(String.join(";", parts));
        return sb.toString();
    }

    public static Optional<WardrobeView> decodeWardrobe(String text) {
        if (text == null) {
            return Optional.empty();
        }
        if (text.regionMatches(true, 0, "WARDROBE|v2|", 0, 12)) {
            return decodeWardrobeV2(text);
        }
        // Legacy v1 (no activeSlotId field)
        if (text.regionMatches(true, 0, "WARDROBE|v1|", 0, 12)) {
            return decodeWardrobeV1(text);
        }
        return Optional.empty();
    }

    private static Optional<WardrobeView> decodeWardrobeV2(String text) {
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
        List<SlotView> slots = parseSlots(p.length >= 7 ? p[6] : "");
        return Optional.of(new WardrobeView(slim, activeUrl, activeCape, activeSlotId, List.copyOf(slots)));
    }

    private static Optional<WardrobeView> decodeWardrobeV1(String text) {
        String[] p = text.split("\\|", -1);
        if (p.length < 5) {
            return Optional.empty();
        }
        boolean slim = "1".equals(p[2]);
        String activeUrl = unb64(p[3]);
        String activeCape = unb64(p[4]);
        List<SlotView> slots = parseSlots(p.length >= 6 ? p[5] : "");
        return Optional.of(new WardrobeView(slim, activeUrl, activeCape, -1L, List.copyOf(slots)));
    }

    private static List<SlotView> parseSlots(String body) {
        List<SlotView> slots = new ArrayList<>();
        if (body == null || body.isBlank()) {
            return slots;
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
        return slots;
    }

    public static String encodeEmoteCatalog(List<EmoteView> emotes) {
        StringBuilder sb = new StringBuilder("EMOTE_CATALOG|v1|");
        List<String> parts = new ArrayList<>();
        for (EmoteView e : emotes) {
            parts.add(e.id() + "," + b64(e.name()));
        }
        sb.append(String.join(";", parts));
        return sb.toString();
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

    public static String uiOk(String message) {
        return "UI|OK|" + (message == null ? "" : message);
    }

    public static String uiErr(String message) {
        return "UI|ERR|" + (message == null ? "error" : message);
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

    public static String normalizeModelToken(String token) {
        if (token == null) {
            return "0";
        }
        String t = token.trim().toLowerCase(Locale.ROOT);
        if ("1".equals(t) || "slim".equals(t) || "true".equals(t)) {
            return "1";
        }
        return "0";
    }
}
