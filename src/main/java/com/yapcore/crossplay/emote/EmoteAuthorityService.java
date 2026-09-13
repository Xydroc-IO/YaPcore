package com.yapcore.crossplay.emote;

import com.google.gson.JsonObject;
import com.yapcore.crossplay.bedrock.parity.ParityBand;
import com.yapcore.crossplay.bedrock.parity.ParityCatalogs;
import com.yapcore.crossplay.bedrock.parity.convert.ConvertPipeline;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Phase 2 emote authority: catalog-gated play/stop with Bedrock UUID keys.
 * Broadcast hooks fan out to JE ({@code yap:presence}) and Bedrock {@code EmotePacket}.
 */
public final class EmoteAuthorityService {

    private static final Logger LOG = Logger.getLogger("YaPcore.Emote");

    public record PlayEvent(
            UUID playerUuid,
            String username,
            String emoteId,
            EmoteClip clip,
            String source) {
    }

    private final ParityBand band;
    private final ParityCatalogs catalogs;
    private final EmoteClipRegistry clips;
    private final long cooldownMs;
    private final ConcurrentHashMap<UUID, Long> lastPlayMs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> activeEmote = new ConcurrentHashMap<>();
    private volatile Consumer<PlayEvent> onPlay = e -> {
    };

    public EmoteAuthorityService(ParityBand band, long cooldownMs) {
        this.band = Objects.requireNonNull(band, "band");
        this.catalogs = ParityCatalogs.load(band);
        this.clips = EmoteClipRegistry.load(band);
        this.cooldownMs = Math.max(0L, cooldownMs);
        LOG.info("Emote authority ready band=" + band.id()
                + " catalog=" + catalogs.emotes().size()
                + " clips=" + clips.size()
                + " missingClips=" + clips.missingClips());
    }

    public static EmoteAuthorityService createDefault() {
        return new EmoteAuthorityService(ParityBand.of(ParityBand.DEFAULT), 1_500L);
    }

    public void setOnPlay(Consumer<PlayEvent> onPlay) {
        this.onPlay = onPlay != null ? onPlay : e -> {
        };
    }

    public ParityBand band() {
        return band;
    }

    public EmoteClipRegistry clips() {
        return clips;
    }

    public ParityCatalogs catalogs() {
        return catalogs;
    }

    public boolean isCatalogEmote(String emoteId) {
        return catalogs.emote(emoteId).isPresent();
    }

    /**
     * Validate + cooldown + fire hooks. Returns empty when rejected.
     *
     * @param source {@code BE}, {@code JE}, {@code CMD}, or {@code HTTP}
     */
    public Optional<PlayEvent> tryPlay(UUID playerUuid, String username, String emoteId, String source) {
        if (playerUuid == null || emoteId == null || emoteId.isBlank()) {
            return Optional.empty();
        }
        String id = emoteId.trim();
        if (catalogs.emote(id).isEmpty()) {
            LOG.fine(() -> "Reject unknown emote id=" + id + " from " + source);
            return Optional.empty();
        }
        EmoteClip clip = clips.byId(id).orElseGet(() -> catalogOnlyClip(catalogs.emote(id).orElseThrow()));
        long now = System.currentTimeMillis();
        Long prev = lastPlayMs.get(playerUuid);
        if (prev != null && cooldownMs > 0L && (now - prev) < cooldownMs) {
            return Optional.empty();
        }
        lastPlayMs.put(playerUuid, now);
        activeEmote.put(playerUuid, id);
        PlayEvent event = new PlayEvent(
                playerUuid,
                username == null ? "" : username,
                id,
                clip,
                source == null ? "" : source);
        try {
            onPlay.accept(event);
        } catch (Exception e) {
            LOG.warning("Emote onPlay hook failed: " + e.getMessage());
        }
        return Optional.of(event);
    }

    public Optional<PlayEvent> tryPlayByName(UUID playerUuid, String username, String nameOrSlug, String source) {
        Optional<EmoteClip> fromClip = clips.byNameOrSlug(nameOrSlug);
        if (fromClip.isPresent()) {
            return tryPlay(playerUuid, username, fromClip.get().bedrockEmoteId(), source);
        }
        return resolveCatalogId(nameOrSlug)
                .flatMap(id -> tryPlay(playerUuid, username, id, source));
    }

    private Optional<String> resolveCatalogId(String nameOrSlug) {
        if (nameOrSlug == null || nameOrSlug.isBlank()) {
            return Optional.empty();
        }
        String key = nameOrSlug.trim();
        if (catalogs.emote(key).isPresent()) {
            return Optional.of(key);
        }
        String lower = key.toLowerCase(Locale.ROOT);
        for (ParityCatalogs.CatalogEntry e : catalogs.emotes().entries()) {
            String name = e.string("name");
            if (name != null && name.equalsIgnoreCase(key)) {
                return Optional.of(e.id());
            }
            String slug = ConvertPipeline.emoteFixtureSlug(e);
            if (slug.equalsIgnoreCase(lower)) {
                return Optional.of(e.id());
            }
        }
        return Optional.empty();
    }

    /** UUID/name/duration only — no bone tracks until vanilla RP extract lands. */
    private static EmoteClip catalogOnlyClip(ParityCatalogs.CatalogEntry e) {
        String name = e.string("name") != null ? e.string("name") : e.id();
        String anim = e.string("animation") != null ? e.string("animation") : "";
        JsonObject clip = new JsonObject();
        clip.addProperty("loop", false);
        clip.addProperty("length", 2.0);
        clip.add("bones", new JsonObject());
        return new EmoteClip(
                e.id(),
                name,
                anim,
                false,
                2.0,
                clip,
                ConvertPipeline.emoteFixtureSlug(e),
                "");
    }

    public void stop(UUID playerUuid) {
        if (playerUuid != null) {
            activeEmote.remove(playerUuid);
        }
    }

    public Optional<String> activeEmoteId(UUID playerUuid) {
        if (playerUuid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(activeEmote.get(playerUuid));
    }

    /** Linear interpolate a bone rotation track at {@code t} seconds (test + client helper). */
    public static float[] sampleRotation(EmoteClip clip, String bone, double tSeconds) {
        if (clip == null || bone == null) {
            return new float[] {0f, 0f, 0f};
        }
        var tracks = clip.boneTracks().get(bone);
        if (tracks == null || !tracks.containsKey("rotation")) {
            return new float[] {0f, 0f, 0f};
        }
        MapLike keys = MapLike.of(tracks.get("rotation"));
        return keys.sample(tSeconds);
    }

    /** Tiny helper so tests don't need Guava. */
    private record MapLike(java.util.NavigableMap<Double, float[]> nav) {
        static MapLike of(java.util.Map<Double, float[]> raw) {
            java.util.TreeMap<Double, float[]> t = new java.util.TreeMap<>();
            t.putAll(raw);
            return new MapLike(t);
        }

        float[] sample(double t) {
            if (nav.isEmpty()) {
                return new float[] {0f, 0f, 0f};
            }
            var floor = nav.floorEntry(t);
            var ceil = nav.ceilingEntry(t);
            if (floor == null) {
                return ceil.getValue().clone();
            }
            if (ceil == null || floor.getKey().equals(ceil.getKey())) {
                return floor.getValue().clone();
            }
            double t0 = floor.getKey();
            double t1 = ceil.getKey();
            float a = (float) ((t - t0) / (t1 - t0));
            float[] a0 = floor.getValue();
            float[] a1 = ceil.getValue();
            return new float[] {
                    a0[0] + (a1[0] - a0[0]) * a,
                    a0[1] + (a1[1] - a0[1]) * a,
                    a0[2] + (a1[2] - a0[2]) * a
            };
        }
    }
}
