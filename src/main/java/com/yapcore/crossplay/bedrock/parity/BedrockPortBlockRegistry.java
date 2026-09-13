package com.yapcore.crossplay.bedrock.parity;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yapcore.crossplay.bedrock.parity.convert.ConvertPipeline;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Phase 4: frozen catalog blocks loaded from converted {@code yap.blockstate/1} artifacts.
 * Maps Bedrock id / JE port id / runtime index for bridge + Folia placement.
 */
public final class BedrockPortBlockRegistry {

    public enum Placement {
        /** JE {@code LIGHT} with level from catalog id. */
        NATIVE_LIGHT,
        /** JE {@code STONECUTTER}. */
        NATIVE_STONECUTTER,
        /** JE item frame / glow item frame entity. */
        NATIVE_FRAME,
        /**
         * Solid exclusive/chemistry: Folia carrier {@code BARRIER} + {@code BlockDisplay}
         * showing convert-verified Bedrock textures. Not NoteBlock/CMD instrument fakes.
         */
        CARRIER_DISPLAY
    }

    public record PortBlock(
            String bedrockId,
            String jePortId,
            String modelPath,
            int bedrockRuntimeIndex,
            String kind,
            Placement placement,
            int lightLevel,
            boolean glowFrame) {
        public String shortName() {
            int i = bedrockId.indexOf(':');
            return i >= 0 ? bedrockId.substring(i + 1) : bedrockId;
        }
    }

    private final ParityBand band;
    private final Map<String, PortBlock> byBedrockId;
    private final Map<String, PortBlock> byJePortId;
    private final Map<Integer, PortBlock> byRuntimeIndex;

    private static volatile BedrockPortBlockRegistry CACHED_DEFAULT;

    private BedrockPortBlockRegistry(
            ParityBand band,
            Map<String, PortBlock> byBedrockId,
            Map<String, PortBlock> byJePortId,
            Map<Integer, PortBlock> byRuntimeIndex) {
        this.band = band;
        this.byBedrockId = byBedrockId;
        this.byJePortId = byJePortId;
        this.byRuntimeIndex = byRuntimeIndex;
    }

    public static BedrockPortBlockRegistry loadDefault() {
        BedrockPortBlockRegistry local = CACHED_DEFAULT;
        if (local != null) {
            return local;
        }
        synchronized (BedrockPortBlockRegistry.class) {
            if (CACHED_DEFAULT == null) {
                CACHED_DEFAULT = load(ParityBand.of(ParityBand.DEFAULT));
            }
            return CACHED_DEFAULT;
        }
    }

    public static BedrockPortBlockRegistry load(ParityBand band) {
        Objects.requireNonNull(band, "band");
        ParityCatalogs catalogs = ParityCatalogs.load(band);
        Map<String, PortBlock> byBe = new LinkedHashMap<>();
        Map<String, PortBlock> byJe = new LinkedHashMap<>();
        Map<Integer, PortBlock> byRt = new LinkedHashMap<>();

        for (ParityCatalogs.CatalogEntry e : catalogs.blocks().entries()) {
            String slug = ConvertPipeline.blockIdToFixtureFile(e.id()).replace(".json", "");
            String path = band.convertedPath("block/" + slug + ".yapblock.json");
            JsonObject root = readJson(path);
            if (!"yap.blockstate/1".equals(root.get("format").getAsString())) {
                throw new IllegalStateException("Bad yapblock format: " + path);
            }
            String bedrockId = root.get("bedrock_id").getAsString();
            if (!e.id().equals(bedrockId)) {
                throw new IllegalStateException("Catalog/yapblock id mismatch " + e.id() + " vs " + bedrockId);
            }
            String jePortId = root.get("je_port_id").getAsString();
            String model = root.has("model") ? root.get("model").getAsString() : ("yapbedrock:block/" + shortName(bedrockId));
            int runtime = root.has("bedrock_runtime_index") ? root.get("bedrock_runtime_index").getAsInt() : -1;
            String kind = e.string("kind") != null ? e.string("kind") : "exclusive";
            Placement placement = placementFor(bedrockId, kind);
            int light = lightLevelFor(bedrockId);
            boolean glow = "minecraft:glow_frame".equals(bedrockId);
            PortBlock pb = new PortBlock(bedrockId, jePortId, model, runtime, kind, placement, light, glow);
            byBe.put(bedrockId, pb);
            byJe.put(jePortId, pb);
            if (runtime >= 0) {
                byRt.put(runtime, pb);
            }
        }
        if (byBe.size() != catalogs.blocks().size()) {
            throw new IllegalStateException("Port registry size mismatch");
        }
        return new BedrockPortBlockRegistry(
                band,
                Collections.unmodifiableMap(byBe),
                Collections.unmodifiableMap(byJe),
                Collections.unmodifiableMap(byRt));
    }

    private static Placement placementFor(String bedrockId, String kind) {
        if (bedrockId.startsWith("minecraft:light_block_")) {
            return Placement.NATIVE_LIGHT;
        }
        if ("minecraft:stonecutter_block".equals(bedrockId)) {
            return Placement.NATIVE_STONECUTTER;
        }
        if ("minecraft:frame".equals(bedrockId) || "minecraft:glow_frame".equals(bedrockId)) {
            return Placement.NATIVE_FRAME;
        }
        return Placement.CARRIER_DISPLAY;
    }

    private static int lightLevelFor(String bedrockId) {
        if (!bedrockId.startsWith("minecraft:light_block_")) {
            return -1;
        }
        try {
            return Integer.parseInt(bedrockId.substring("minecraft:light_block_".length()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String shortName(String bedrockId) {
        int i = bedrockId.indexOf(':');
        return i >= 0 ? bedrockId.substring(i + 1) : bedrockId;
    }

    private static JsonObject readJson(String path) {
        try (InputStream in = BedrockPortBlockRegistry.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + path);
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read " + path, e);
        }
    }

    public ParityBand band() {
        return band;
    }

    public int size() {
        return byBedrockId.size();
    }

    public List<PortBlock> all() {
        return new ArrayList<>(byBedrockId.values());
    }

    public Optional<PortBlock> byBedrockId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byBedrockId.get(id.trim()));
    }

    public Optional<PortBlock> byJePortId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byJePortId.get(id.trim()));
    }

    public Optional<PortBlock> byRuntimeIndex(int runtimeIndex) {
        return Optional.ofNullable(byRuntimeIndex.get(runtimeIndex));
    }

    public Optional<PortBlock> resolve(String bedrockOrJeOrShort) {
        if (bedrockOrJeOrShort == null || bedrockOrJeOrShort.isBlank()) {
            return Optional.empty();
        }
        String s = bedrockOrJeOrShort.trim();
        Optional<PortBlock> hit = byBedrockId(s);
        if (hit.isPresent()) {
            return hit;
        }
        hit = byJePortId(s);
        if (hit.isPresent()) {
            return hit;
        }
        if (!s.contains(":")) {
            hit = byBedrockId("minecraft:" + s);
            if (hit.isPresent()) {
                return hit;
            }
            hit = byJePortId("yapbedrock:" + s);
            if (hit.isPresent()) {
                return hit;
            }
        }
        String lower = s.toLowerCase(Locale.ROOT);
        for (PortBlock pb : byBedrockId.values()) {
            if (pb.shortName().equalsIgnoreCase(lower) || pb.jePortId().equalsIgnoreCase(s)) {
                return Optional.of(pb);
            }
        }
        return Optional.empty();
    }
}
