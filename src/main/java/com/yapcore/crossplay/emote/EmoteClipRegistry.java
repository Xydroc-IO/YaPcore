package com.yapcore.crossplay.emote;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yapcore.crossplay.bedrock.parity.ParityBand;
import com.yapcore.crossplay.bedrock.parity.ParityCatalogs;
import com.yapcore.crossplay.bedrock.parity.convert.ConvertPipeline;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Loads converted {@code yap.emote/1} artifacts for a parity band.
 * Missing clips are skipped — free emote bone data is not in Mojang bedrock-samples;
 * set {@code YAP_BEDROCK_VANILLA_RP} and re-extract to populate.
 */
public final class EmoteClipRegistry {

    private static final Logger LOG = Logger.getLogger("YaPcore.EmoteClips");

    private final ParityBand band;
    private final Map<String, EmoteClip> byId;
    private final Map<String, EmoteClip> byNameLower;
    private final int catalogSize;
    private final int missingClips;

    private EmoteClipRegistry(
            ParityBand band,
            Map<String, EmoteClip> byId,
            Map<String, EmoteClip> byNameLower,
            int catalogSize,
            int missingClips) {
        this.band = band;
        this.byId = byId;
        this.byNameLower = byNameLower;
        this.catalogSize = catalogSize;
        this.missingClips = missingClips;
    }

    public static EmoteClipRegistry load(ParityBand band) {
        ParityCatalogs catalogs = ParityCatalogs.load(band);
        Map<String, EmoteClip> byId = new LinkedHashMap<>();
        Map<String, EmoteClip> byName = new LinkedHashMap<>();
        int missing = 0;
        for (ParityCatalogs.CatalogEntry e : catalogs.emotes().entries()) {
            String slug = ConvertPipeline.emoteFixtureSlug(e);
            String rel = "emote/" + slug + ".yapemote.json";
            String path = band.convertedPath(rel);
            try (InputStream in = EmoteClipRegistry.class.getClassLoader().getResourceAsStream(path)) {
                if (in == null) {
                    missing++;
                    continue;
                }
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                if (!"yap.emote/1".equals(root.get("format").getAsString())) {
                    LOG.warning("Bad emote format in " + path + " — skipping");
                    missing++;
                    continue;
                }
                String id = root.get("bedrock_emote_id").getAsString();
                if (!e.id().equals(id)) {
                    LOG.warning("Emote id mismatch catalog=" + e.id() + " file=" + id + " — skipping");
                    missing++;
                    continue;
                }
                JsonObject clip = root.getAsJsonObject("clip");
                boolean loop = clip.has("loop") && clip.get("loop").getAsBoolean();
                double length = clip.has("length") ? clip.get("length").getAsDouble() : 0.0;
                String name = root.has("name") ? root.get("name").getAsString() : e.string("name");
                String anim = root.has("bedrock_animation")
                        ? root.get("bedrock_animation").getAsString()
                        : e.string("animation");
                EmoteClip loaded = new EmoteClip(id, name, anim, loop, length, clip, slug, rel);
                byId.put(id, loaded);
                if (name != null && !name.isBlank()) {
                    byName.put(name.toLowerCase(Locale.ROOT), loaded);
                }
                byName.put(slug.toLowerCase(Locale.ROOT), loaded);
            } catch (Exception ex) {
                LOG.warning("Failed to load emote " + path + ": " + ex.getMessage());
                missing++;
            }
        }
        if (missing > 0) {
            LOG.warning("Emote clips loaded=" + byId.size() + "/" + catalogs.emotes().size()
                    + " missing=" + missing
                    + " (need YAP_BEDROCK_VANILLA_RP extract for JE bone playback)");
        }
        return new EmoteClipRegistry(
                band,
                Collections.unmodifiableMap(byId),
                Collections.unmodifiableMap(byName),
                catalogs.emotes().size(),
                missing);
    }

    public static EmoteClipRegistry loadDefault() {
        return load(ParityBand.of(ParityBand.DEFAULT));
    }

    public ParityBand band() {
        return band;
    }

    public int size() {
        return byId.size();
    }

    public int catalogSize() {
        return catalogSize;
    }

    public int missingClips() {
        return missingClips;
    }

    public Optional<EmoteClip> byId(String bedrockEmoteId) {
        if (bedrockEmoteId == null || bedrockEmoteId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(bedrockEmoteId.trim()));
    }

    public Optional<EmoteClip> byNameOrSlug(String nameOrSlug) {
        if (nameOrSlug == null || nameOrSlug.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byNameLower.get(nameOrSlug.trim().toLowerCase(Locale.ROOT)));
    }

    public Map<String, EmoteClip> allById() {
        return byId;
    }
}
