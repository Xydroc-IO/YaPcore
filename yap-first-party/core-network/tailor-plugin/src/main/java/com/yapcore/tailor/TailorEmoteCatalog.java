package com.yapcore.tailor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Frozen Bedrock emote catalog (band_26_50) — same IDs as chassis
 * {@code protocol/bedrock/parity/band_26_50/catalogs/emotes.v1.json}.
 */
public final class TailorEmoteCatalog {

    public record Entry(String id, String name, String animation, String slug) {
    }

    private static final TailorEmoteCatalog INSTANCE = loadFrozen();

    private final Map<String, Entry> byId;
    private final Map<String, Entry> byNameLower;

    private TailorEmoteCatalog(Map<String, Entry> byId, Map<String, Entry> byNameLower) {
        this.byId = byId;
        this.byNameLower = byNameLower;
    }

    public static TailorEmoteCatalog get() {
        return INSTANCE;
    }

    private static TailorEmoteCatalog loadFrozen() {
        // Lockstep with chassis emotes.v1.json (v2 — free persona/pieces extracts only)
        String[][] rows = {
                {"4c8ae710-df2e-47cd-814d-cc7bf21a3d67", "Wave", "animation.overhead_wave"},
                {"9a469a61-c83b-4ba9-b507-bdbe64430582", "Simple Clap", "animation.basic_clap"},
                {"ce5c0300-7f03-455d-aaf1-352e4927b54d", "Over There!", "animation.communication_point"},
                {"17428c4c-3813-4ea1-b3a9-d6a32f83afca", "Follow Me", "animation.communication_follow_me"},
        };
        Map<String, Entry> byId = new LinkedHashMap<>();
        Map<String, Entry> byName = new LinkedHashMap<>();
        for (String[] r : rows) {
            String slug = slugify(r[1]);
            Entry e = new Entry(r[0], r[1], r[2], slug);
            byId.put(e.id(), e);
            byName.put(e.name().toLowerCase(Locale.ROOT), e);
            byName.put(slug, e);
        }
        return new TailorEmoteCatalog(
                Collections.unmodifiableMap(byId),
                Collections.unmodifiableMap(byName));
    }

    public Optional<Entry> byId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(id.trim()));
    }

    public Optional<Entry> byNameOrSlug(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byNameLower.get(name.trim().toLowerCase(Locale.ROOT)));
    }

    public Optional<Entry> resolve(String idOrName) {
        Optional<Entry> hit = byId(idOrName);
        return hit.isPresent() ? hit : byNameOrSlug(idOrName);
    }

    public List<Entry> entries() {
        return List.copyOf(byId.values());
    }

    public List<String> names() {
        List<String> out = new ArrayList<>();
        for (Entry e : byId.values()) {
            out.add(e.name());
        }
        return out;
    }

    public int size() {
        return byId.size();
    }

    public static String slugify(String raw) {
        String s = raw.toLowerCase(Locale.ROOT)
                .replace('!', ' ')
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_|_$", "");
        return s.isBlank() ? "unnamed" : s;
    }
}
